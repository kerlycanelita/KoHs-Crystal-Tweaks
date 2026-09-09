#!/usr/bin/env python3
"""Verify a built JAR against that version's real Minecraft classes and dependency metadata.

Two checks that nothing else performs:

1. Every Mixin target actually exists in that Minecraft version's classes.
2. The Mod Menu version suggested by the JAR actually supports that Minecraft version.
3. Only rendering and audio Mixins are allowed to cancel. A cancellable injection anywhere on the
   path from a click to its packet is how a mod silently swallows an input, so that is refused.

Mixin resolves @Mixin / @Inject / @Invoker / @Accessor targets at runtime, not at compile
time, so a JAR whose targets do not exist compiles cleanly and then aborts the game on
launch. Loom writes the JAR in the `intermediary` namespace for the 1.x targets and in
`official` for 26.x, so each JAR is checked against Minecraft classes in its own namespace.

Usage:  python tools/verify-mixins.py [versions/*.jar ...]
"""
import glob
import json
import os
import re
import subprocess
import sys
import zipfile

LOOM = os.path.expanduser('~/.gradle/caches/fabric-loom')
MAVEN = os.path.join(LOOM, 'minecraftMaven', 'net', 'minecraft')


def minecraft_jars(mc_version, namespace):
    """Return the Minecraft JARs holding the classes this mod JAR was remapped against."""
    if namespace == 'official':
        found = [os.path.join(LOOM, mc_version, n)
                 for n in ('minecraft-client-only.jar', 'minecraft-common.jar')]
        return [p for p in found if os.path.exists(p)]

    jars = []
    for part in ('minecraft-clientonly-intermediary', 'minecraft-common-intermediary'):
        base = os.path.join(MAVEN, part)
        if not os.path.isdir(base):
            continue
        # Several mapping flavours are cached per version; any of them carries the same
        # intermediary names, so the first match is enough.
        for entry in sorted(os.listdir(base)):
            if entry.split('-')[0] != mc_version:
                continue
            for f in sorted(glob.glob(os.path.join(base, entry, '*.jar'))):
                if f.endswith('.backup'):
                    continue
                jars.append(f)
                break
            break
    return jars


def index_classes(jars):
    """Map internal class name -> (jar, entry) for every class in the Minecraft JARs."""
    index = {}
    for j in jars:
        with zipfile.ZipFile(j) as z:
            for n in z.namelist():
                if n.endswith('.class'):
                    index.setdefault(n[:-6], (j, n))
    return index


_members_cache = {}


def members_of(jar, internal_name):
    """Return {(name, descriptor)} plus {name} for a class, read straight from the JAR."""
    key = (jar, internal_name)
    if key in _members_cache:
        return _members_cache[key]
    out = subprocess.run(['javap', '-p', '-cp', jar, internal_name.replace('/', '.')],
                         capture_output=True, text=True)
    names = set()
    for line in out.stdout.splitlines():
        line = line.strip().rstrip(';')
        m = re.search(r'([A-Za-z_$][\w$]*)\s*\(', line)
        if m:
            names.add(m.group(1))
            continue
        m = re.search(r'([A-Za-z_$][\w$]*)$', line)
        if m:
            names.add(m.group(1))
    _members_cache[key] = names
    return names


# Cancelling one of these cannot lose an input: the worst it can do is skip a draw or a sound.
CANCEL_ALLOWED = ('Renderer', 'Model', 'SoundBufferLibrary', 'SoundEngine')

_descriptor_cache = {}


def descriptors_of(jar, internal_name):
    """Return {name(argDescriptors)returnDescriptor} for every method, as Mixin writes them."""
    key = (jar, internal_name)
    if key in _descriptor_cache:
        return _descriptor_cache[key]
    out = subprocess.run(['javap', '-p', '-s', '-cp', jar, internal_name.replace('/', '.')],
                         capture_output=True, text=True)
    result = set()
    pending = None
    for line in out.stdout.splitlines():
        stripped = line.strip()
        m = re.search(r'([A-Za-z_$][\w$]*)\s*\(', stripped)
        if m and not stripped.startswith('descriptor:'):
            pending = m.group(1)
        elif stripped.startswith('descriptor:') and pending:
            result.add(pending + stripped.split('descriptor:', 1)[1].strip())
            pending = None
    _descriptor_cache[key] = result
    return result


ANNOT = re.compile(r'org\.spongepowered\.asm\.mixin\.([\w.]+)\(([^\n]*(?:\n\s+[^\n]*)*?)\n\s*\)',
                   re.M)


def parse_mixin_class(class_file):
    """Extract the @Mixin targets and every member-level target from one compiled mixin."""
    out = subprocess.run(['javap', '-v', '-p', class_file], capture_output=True, text=True)
    text = out.stdout
    targets = re.findall(r'Mixin\(\s*value=\[class L([\w/$]+);', text)
    if not targets:
        targets = re.findall(r'Lorg/spongepowered/asm/mixin/Mixin;[\s\S]{0,200}?L([\w/$]+);', text)

    cancels = 'cancellable=true' in text.replace(' ', '')

    members = []
    for kind, body in re.findall(
            r'org\.spongepowered\.asm\.mixin\.(?:injection\.)?(?:gen\.)?(\w+)\(([\s\S]{0,400}?)\n\s*\)',
            text):
        if kind in ('Invoker', 'Accessor'):
            m = re.search(r'value="([^"]+)"', body)
            if m:
                members.append((kind, m.group(1)))
        elif kind in ('Inject', 'ModifyVariable', 'Redirect', 'ModifyArg', 'ModifyArgs'):
            m = re.search(r'method=(?:\[)?"([^"]+)"', body)
            if m:
                members.append((kind, m.group(1)))
    return targets, members, cancels


MODMENU = os.path.expanduser(
    '~/.gradle/caches/modules-2/files-2.1/com.terraformersmc/modmenu')


def suggested_modmenu_supports(mc_version, suggested):
    """Check the suggested Mod Menu really runs on this Minecraft version.

    A suggestion pointing at a Mod Menu built for a different Minecraft version is not fatal for
    players, who simply cannot install what the mod page recommends, but it does break `runClient`,
    because Loom resolves the same version as a real runtime dependency.
    """
    want = suggested.lstrip('>=~ ')
    # Gradle stores each artifact under an extra per-file checksum directory, so the JAR is one
    # level deeper than the version directory.
    matches = [m for m in glob.glob(os.path.join(MODMENU, want, '*', '*.jar'))
               if not m.endswith(('-sources.jar', '-javadoc.jar'))]
    if not matches:
        return None  # not cached locally; nothing to check against
    jar = matches[0]
    with zipfile.ZipFile(jar) as z:
        meta = json.loads(z.read('fabric.mod.json'))
    predicate = meta.get('depends', {}).get('minecraft')
    if predicate is None:
        return None
    if isinstance(predicate, str):
        predicate = [predicate]

    def part(v):
        return [int(x) for x in re.findall(r'\d+', v)]

    target = part(mc_version)
    for rule in predicate:
        for bound in re.findall(r'<(?!=)\s*([\w.\-]+)', rule):
            if target >= part(bound):
                return False
    return True


def check_jar(path):
    with zipfile.ZipFile(path) as z:
        manifest = z.read('META-INF/MANIFEST.MF').decode().replace('\r\n ', '').replace('\n ', '')
        cfg_name = next(n for n in z.namelist() if n.endswith('.mixins.json'))
        cfg = json.loads(z.read(cfg_name))
        mc = re.search(r'Fabric-Minecraft-Version:\s*(\S+)', manifest).group(1)
        ns = re.search(r'Fabric-Mapping-Namespace:\s*(\S+)', manifest).group(1)
        pkg = cfg['package'].replace('.', '/')
        listed = list(cfg.get('client', [])) + list(cfg.get('mixins', []))

        problems_meta = []
        suggested = json.loads(z.read('fabric.mod.json')).get('suggests', {}).get('modmenu')
        if suggested:
            ok = suggested_modmenu_supports(mc, suggested)
            if ok is False:
                problems_meta.append(
                    f'suggests Mod Menu {suggested}, which declares it does not run on {mc}')

        jars = minecraft_jars(mc, ns)
        if not jars:
            return mc, ns, problems_meta + [
                f'no cached Minecraft classes for {mc} ({ns}); cannot verify targets']
        index = index_classes(jars)

        problems = list(problems_meta)
        tmp = os.path.join(os.path.dirname(path) or '.', '.mixincheck')
        os.makedirs(tmp, exist_ok=True)
        for name in listed:
            entry = f'{pkg}/{name}.class'
            if entry not in z.namelist():
                problems.append(f'{name}: listed in mixin config but missing from the JAR')
                continue
            local = os.path.join(tmp, name + '.class')
            with open(local, 'wb') as fh:
                fh.write(z.read(entry))
            targets, members, cancels = parse_mixin_class(local)
            if not targets:
                problems.append(f'{name}: no @Mixin target could be read')
                continue
            # In the intermediary namespace the target class name is obfuscated, so fall back to
            # this project's own Mixin class name, which is never remapped.
            searchable = list(targets) + [name]
            if cancels and not any(a in t for t in searchable for a in CANCEL_ALLOWED):
                problems.append(
                    f'{name}: cancels a Vanilla method on {", ".join(targets)}, which is not a '
                    f'rendering or audio class; a cancellable injection there can swallow an input')

            for t in targets:
                if t not in index:
                    problems.append(f'{name}: target class {t} does not exist in Minecraft {mc}')
                    continue
                jar, _ = index[t]
                have = members_of(jar, t)
                descriptors = descriptors_of(jar, t)
                for kind, member in members:
                    bare = member.split('(')[0]
                    if bare not in have:
                        problems.append(
                            f'{name}: @{kind} target "{bare}" not found in {t} (Minecraft {mc})')
                    elif '(' in member and member not in descriptors:
                        # Matching the name is not enough: an @Inject whose descriptor does not
                        # exist resolves to nothing and the injection silently never runs.
                        problems.append(
                            f'{name}: @{kind} target "{member}" has no matching signature in {t} '
                            f'(Minecraft {mc})')
            os.remove(local)
        try:
            os.rmdir(tmp)
        except OSError:
            pass
    return mc, ns, problems


def main(argv):
    paths = argv[1:] or sorted(glob.glob('versions/*.jar'))
    if not paths:
        print('no JARs given and versions/ is empty')
        return 1
    bad = 0
    for p in paths:
        mc, ns, problems = check_jar(p)
        tag = f'{os.path.basename(p)}  [mc {mc}, {ns}]'
        if problems:
            bad += 1
            print(f'  FAIL {tag}')
            for pr in problems:
                print(f'         {pr}')
        else:
            print(f'  OK   {tag}')
    print()
    print(f'checked: {len(paths)}  problems: {bad}')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
