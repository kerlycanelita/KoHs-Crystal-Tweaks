package com.zymekoh.crystaltweaks.client.benchmark;

import com.zymekoh.crystaltweaks.client.compat.HerziumBridge;
import com.zymekoh.crystaltweaks.core.CrystalOptimizerGuard;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Turns a benchmark run into the lines the screen shows, in one of two voices: plain words for a
 * player, and the numbers, definitions and caveats for a developer.
 *
 * <p>Nothing here invents a figure. A line that has no samples behind it says so instead of
 * showing a zero, and a comparison is only drawn against a run that really was taken.</p>
 */
final class BenchmarkReport {
    enum Kind { HEADING, TEXT, VALUE, NOTE, GOOD, WARN, SPACER }

    record Line(Kind kind, String text, String value) {
        static Line heading(String text) { return new Line(Kind.HEADING, text, ""); }
        static Line text(String text) { return new Line(Kind.TEXT, text, ""); }
        static Line value(String label, String value) { return new Line(Kind.VALUE, label, value); }
        static Line note(String text) { return new Line(Kind.NOTE, text, ""); }
        static Line good(String text) { return new Line(Kind.GOOD, text, ""); }
        static Line warn(String text) { return new Line(Kind.WARN, text, ""); }
        static Line spacer() { return new Line(Kind.SPACER, "", ""); }
    }

    private final boolean spanish;
    private final boolean dev;
    private final List<Line> lines = new ArrayList<>();

    private BenchmarkReport(boolean spanish, boolean dev) {
        this.spanish = spanish;
        this.dev = dev;
    }

    static List<Line> build(boolean spanish, boolean dev, BenchmarkRun latest, BenchmarkRun previous,
            boolean recording) {
        BenchmarkReport report = new BenchmarkReport(spanish, dev);
        report.setup();
        if (recording) {
            report.recording();
        } else if (latest == null) {
            report.empty();
        } else {
            report.results(latest);
            if (previous != null) {
                report.comparison(latest, previous);
            }
        }
        report.method();
        return report.lines;
    }

    private String t(String spanish, String english) {
        return this.spanish ? spanish : english;
    }

    // ------------------------------------------------------------------------------------------

    private void setup() {
        this.lines.add(Line.heading(t("Optimizadores detectados", "Detected optimizers")));
        List<String> optimizers = CrystalBenchmark.detectedOptimizers();
        if (optimizers.isEmpty()) {
            this.lines.add(Line.text(t("Ningún optimizador de cristales instalado aparte de Crystal Tweaks.",
                    "No crystal optimizer installed other than Crystal Tweaks.")));
        } else {
            for (String optimizer : optimizers) {
                this.lines.add(Line.good("✔ " + optimizer));
            }
        }
        String state = switch (CrystalOptimizerGuard.pauseReason()) {
            case FORCED_OFF -> t("apagado por ti (Forzar apagado)", "turned off by you (Force off)");
            case CONFLICT -> t("en pausa: cede ante ", "paused: stands down for ") + CrystalOptimizerGuard.conflictingModName();
            case CHECKING -> t("comprobando compatibilidad…", "checking compatibility…");
            case NONE -> t("activo (rotura instantánea en pantalla)", "active (instant break on screen)");
        };
        this.lines.add(Line.value("Crystal Tweaks", state));
        if (HerziumBridge.installed()) {
            String order = HerziumBridge.hotbarOrder();
            this.lines.add(Line.value("Herzium " + HerziumBridge.version(),
                    order.isEmpty() ? t("instalado", "installed") : t("orden: ", "order: ") + order.toLowerCase(Locale.ROOT)));
        }
        if (this.dev) {
            this.lines.add(Line.note(t("Se detecta por id conocido (Marlow's, HCsCR, Client Side Crystals…) o por un nombre que une \"crystal\" con optimizar o client-side. Los mods de rendimiento (Sodium, Lithium, Krypton…) nunca cuentan.",
                    "Detected by a known id (Marlow's, HCsCR, Client Side Crystals…) or by a name pairing \"crystal\" with optimizing or client-side. Performance mods (Sodium, Lithium, Krypton…) never count.")));
        }
        this.lines.add(Line.spacer());
    }

    private void recording() {
        this.lines.add(Line.heading(t("Midiendo…", "Measuring…")));
        this.lines.add(Line.text(t("Vuelve al juego y coloca y rompe cristales como siempre. La prueba termina sola tras "
                        + CrystalBenchmark.TARGET_BREAKS + " roturas o 2 minutos; también puedes detenerla aquí.",
                "Go back to the game and place and break crystals as usual. The run ends by itself after "
                        + CrystalBenchmark.TARGET_BREAKS + " breaks or 2 minutes; you can also stop it here.")));
        this.lines.add(Line.value(t("Roturas confirmadas", "Confirmed breaks"),
                CrystalBenchmark.confirmedBreaks() + "/" + CrystalBenchmark.TARGET_BREAKS));
        this.lines.add(Line.value(t("Colocaciones", "Placements"), String.valueOf(CrystalBenchmark.placements())));
        this.lines.add(Line.spacer());
    }

    private void empty() {
        this.lines.add(Line.heading(t("Sin resultados todavía", "No results yet")));
        this.lines.add(Line.text(t("Entra a un mundo o servidor, pulsa «Iniciar» y juega con cristales: colócalos y rómpelos como siempre. Para comparar optimizadores, repite la prueba con otro instalado; se guardan las últimas 12.",
                "Join a world or server, press \"Start\" and play with crystals: place and break them as usual. To compare optimizers, run it again with another one installed; the last 12 runs are kept.")));
        this.lines.add(Line.spacer());
    }

    // ------------------------------------------------------------------------------------------

    private void results(BenchmarkRun run) {
        this.lines.add(Line.heading(t("Última prueba", "Latest run") + " · " + date(run.timestamp())));
        this.lines.add(Line.value(t("Optimizador al mando", "Optimizer in charge"), run.optimizer()));
        this.lines.add(Line.value(t("Dónde", "Where"), environment(run.environment())
                + (run.serverBrand().isBlank() ? "" : " · " + run.serverBrand())
                + (run.reportedPingMillis() > 0 ? String.format(Locale.ROOT, " · ping %.0f ms", run.reportedPingMillis()) : "")));
        if (!run.meaningful()) {
            this.lines.add(Line.warn(t("Muy pocos cristales para sacar conclusiones: hacen falta al menos 3 colocaciones y 3 roturas confirmadas.",
                    "Too few crystals to draw conclusions: at least 3 placements and 3 confirmed breaks are needed.")));
        }
        if (this.dev) {
            devResults(run);
        } else {
            plainResults(run);
        }
        this.lines.add(Line.spacer());
    }

    private void plainResults(BenchmarkRun run) {
        BenchmarkStats spawn = run.placeToSpawnShown();
        if (spawn.present()) {
            this.lines.add(Line.value(t("Cristal colocado → aparece", "Crystal placed → appears"), ms(spawn.median())));
            this.lines.add(Line.note(t("Es el viaje de ida y vuelta al servidor. Ningún mod del cliente puede acortarlo.",
                    "That is the round trip to the server. No client mod can shorten it.")));
        }
        BenchmarkStats gone = run.attackToGone();
        BenchmarkStats removal = run.attackToRemovalShown();
        if (gone.present()) {
            this.lines.add(Line.value(t("Golpe → desaparece de tu pantalla", "Hit → gone from your screen"), ms(gone.median())));
        }
        if (removal.present()) {
            this.lines.add(Line.value(t("Golpe → el servidor lo confirma", "Hit → the server confirms it"), ms(removal.median())));
        }
        if (gone.present() && removal.present()) {
            double saved = removal.median() - gone.median();
            if (saved >= 5) {
                String who = run.removedEarlyByOtherMod() > run.predictedByCrystalTweaks() ? run.optimizer() : "Crystal Tweaks";
                this.lines.add(Line.good(t(who + " lo hizo desaparecer " + ms(saved) + " antes de la respuesta del servidor.",
                        who + " made it vanish " + ms(saved) + " before the server answered.")));
            } else {
                this.lines.add(Line.note(t("El cristal desaparece cuando responde el servidor, como en Vanilla.",
                        "The crystal vanishes when the server answers, as in Vanilla.")));
            }
        }
        BenchmarkStats gap = run.replaceGap();
        if (gap.present()) {
            this.lines.add(Line.value(t("Golpe → siguiente cristal en el mismo bloque", "Hit → next crystal on the same block"),
                    ms(gap.median())));
            if (removal.present() && gap.median() + 5 < removal.median()) {
                this.lines.add(Line.good(t("Colocaste el siguiente antes de que el servidor confirmara la rotura: eso es lo que acelera un optimizador del lado del servidor como Marlow's.",
                        "You placed the next one before the server confirmed the break: that is what a server-reaching optimizer such as Marlow's speeds up.")));
            } else if (removal.present()) {
                this.lines.add(Line.note(t("Tuviste que esperar a que el servidor quitara el cristal para poner el siguiente. Crystal Tweaks solo lo oculta en tu pantalla; no cambia a qué apuntas.",
                        "You had to wait for the server to remove the crystal before placing the next. Crystal Tweaks only hides it on your screen; it does not change what you aim at.")));
            }
        }
        if (run.crystalsPerSecond() > 0) {
            this.lines.add(Line.value(t("Ritmo en un mismo bloque", "Pace on one block"),
                    String.format(Locale.ROOT, t("%.1f cristales/s", "%.1f crystals/s"), run.crystalsPerSecond())));
        }
        if (run.swapToPlace().present()) {
            this.lines.add(Line.value(t("Cambio de ranura → colocación", "Slot switch → placement"), ms(run.swapToPlace().median())));
        }
        if (run.frameTime().present()) {
            this.lines.add(Line.value(t("Imágenes por segundo", "Frames per second"),
                    String.format(Locale.ROOT, t("%.0f de media · 1%% más bajo %.0f", "%.0f average · 1%% low %.0f"),
                            run.averageFps(), run.onePercentLowFps())));
        }
        if (run.unconfirmedAttacks() > 0) {
            this.lines.add(Line.warn(t(run.unconfirmedAttacks() + " golpes no los confirmó el servidor (fuera de alcance, cristal ya roto o rechazado).",
                    run.unconfirmedAttacks() + " hits were never confirmed by the server (out of reach, already broken or refused).")));
        }
        if (run.placements() > 0 && run.unmatchedPlacements() * 5 >= run.placements()) {
            this.lines.add(Line.note(t(run.unmatchedPlacements() + " de " + run.placements()
                            + " colocaciones no dejaron cristal: al llegar al servidor el bloque seguía ocupado o quedaba fuera de alcance.",
                    run.unmatchedPlacements() + " of " + run.placements()
                            + " placements left no crystal: when they reached the server the block was still taken or out of reach.")));
        }
        verdict(run);
    }

    /** One sentence on what is limiting the player, when the numbers make it clear. */
    private void verdict(BenchmarkRun run) {
        BenchmarkStats gap = run.replaceGap();
        BenchmarkStats removal = run.attackToRemovalShown();
        if (!gap.present() || !removal.present()) {
            return;
        }
        if (gap.median() > removal.median() * 2.5 && gap.median() - removal.median() > 80) {
            this.lines.add(Line.text(t("Conclusión: lo que más tarda es tu propio clic entre cristal y cristal, no la conexión ni el mod.",
                    "Verdict: what takes longest is your own click between crystals, not the connection or the mod.")));
        } else if (removal.median() > 80) {
            this.lines.add(Line.text(t("Conclusión: tu conexión marca el ritmo. Con este ping, solo un optimizador que llegue al servidor (y que tu servidor permita) acelera la siguiente colocación.",
                    "Verdict: your connection sets the pace. At this ping, only an optimizer that reaches the server (and that your server allows) speeds up the next placement.")));
        } else {
            this.lines.add(Line.text(t("Conclusión: conexión rápida y ritmo fluido; el margen que queda es pequeño.",
                    "Verdict: fast connection and a smooth pace; there is little left to gain.")));
        }
    }

    private void devResults(BenchmarkRun run) {
        this.lines.add(Line.value(t("Duración", "Duration"), String.format(Locale.ROOT, "%.1f s", run.durationMillis() / 1000.0D)));
        this.lines.add(Line.value("Minecraft / mod", run.minecraftVersion() + " / " + run.modVersion()));
        this.lines.add(Line.value(t("Colocaciones · golpes · roturas confirmadas", "Placements · hits · confirmed breaks"),
                run.placements() + " · " + run.attacks() + " · " + run.confirmedBreaks()));
        this.lines.add(Line.value(t("Ocultados por Crystal Tweaks · quitados antes por otro mod", "Hidden by Crystal Tweaks · removed early by another mod"),
                run.predictedByCrystalTweaks() + " · " + run.removedEarlyByOtherMod()));
        this.lines.add(Line.value(t("Golpes sin confirmar · colocaciones sin cristal · obsidiana frenada", "Unconfirmed hits · placements without a crystal · obsidian held back"),
                run.unconfirmedAttacks() + " · " + run.unmatchedPlacements() + " · " + run.debounceRefusals()));
        if (!run.herziumOrder().isBlank()) {
            this.lines.add(Line.value(t("Orden de Herzium", "Herzium order"), run.herziumOrder()));
        }
        stat("place→spawn [net]", run.placeToSpawnNetwork());
        stat("place→spawn [shown]", run.placeToSpawnShown());
        stat("attack→explosion [net]", run.attackToExplosionNetwork());
        stat("attack→remove [net]", run.attackToRemovalNetwork());
        stat("attack→remove [shown]", run.attackToRemovalShown());
        stat("attack→not drawn", run.attackToGone());
        stat("attack→next place (same base)", run.replaceGap());
        stat("place→place (same base)", run.cycle());
        stat("hotbar switch→place", run.swapToPlace());
        stat("frame time", run.frameTime());
        if (run.frameTime().present()) {
            this.lines.add(Line.value("FPS avg · 1% low (1000/p99)",
                    String.format(Locale.ROOT, "%.1f · %.1f", run.averageFps(), run.onePercentLowFps())));
        }
        if (run.placeToSpawnNetwork().present() && run.placeToSpawnShown().present()) {
            this.lines.add(Line.note(String.format(Locale.ROOT,
                    t("Cola del hilo del juego (shown − net, mediana de colocación): %.1f ms.",
                            "Game-thread queueing (shown − net, placement median): %.1f ms."),
                    run.placeToSpawnShown().median() - run.placeToSpawnNetwork().median())));
        }
    }

    private void stat(String label, BenchmarkStats stats) {
        if (!stats.present()) {
            this.lines.add(Line.value(label, t("sin muestras", "no samples")));
            return;
        }
        this.lines.add(Line.value(label, String.format(Locale.ROOT, "n=%d  p50 %.1f  p95 %.1f  p99 %.1f", stats.count(),
                stats.median(), stats.p95(), stats.p99())));
        this.lines.add(Line.note(String.format(Locale.ROOT, "      mean %.1f  σ %.1f  min %.1f  max %.1f ms",
                stats.mean(), stats.stdDev(), stats.min(), stats.max())));
    }

    // ------------------------------------------------------------------------------------------

    private void comparison(BenchmarkRun latest, BenchmarkRun previous) {
        this.lines.add(Line.heading(t("Comparación", "Comparison") + " · " + latest.optimizer() + " vs " + previous.optimizer()));
        this.lines.add(Line.note(t("Contra la prueba del " + date(previous.timestamp()) + ". Solo compara bien si fue en el mismo servidor y con un ping parecido.",
                "Against the run from " + date(previous.timestamp()) + ". Only a fair comparison on the same server at a similar ping.")));
        if (Math.abs(latest.reportedPingMillis() - previous.reportedPingMillis()) > 20) {
            this.lines.add(Line.warn(String.format(Locale.ROOT, t("Ojo: el ping cambió de %.0f a %.0f ms entre pruebas.",
                    "Careful: ping changed from %.0f to %.0f ms between runs."), previous.reportedPingMillis(), latest.reportedPingMillis())));
        }
        compare(t("Golpe → desaparece de tu pantalla", "Hit → gone from your screen"), latest.attackToGone(), previous.attackToGone());
        compare(t("Golpe → confirmación del servidor", "Hit → server confirmation"), latest.attackToRemovalShown(), previous.attackToRemovalShown());
        compare(t("Golpe → siguiente cristal", "Hit → next crystal"), latest.replaceGap(), previous.replaceGap());
        compare(t("Colocado → aparece", "Placed → appears"), latest.placeToSpawnShown(), previous.placeToSpawnShown());
        compare(t("Tiempo por imagen", "Frame time"), latest.frameTime(), previous.frameTime());
        this.lines.add(Line.spacer());
    }

    private void compare(String label, BenchmarkStats latest, BenchmarkStats previous) {
        if (!latest.present() || !previous.present()) {
            return;
        }
        double delta = latest.median() - previous.median();
        String arrow = Math.abs(delta) < 2 ? "≈" : delta < 0 ? "▼" : "▲";
        String value = String.format(Locale.ROOT, "%s %s → %s (%s%.0f ms)", arrow, ms(previous.median()), ms(latest.median()),
                delta > 0 ? "+" : "", delta);
        if (this.dev) {
            value += String.format(Locale.ROOT, "  p95 %.1f→%.1f", previous.p95(), latest.p95());
        }
        this.lines.add(Line.value(label, value));
    }

    // ------------------------------------------------------------------------------------------

    private void method() {
        this.lines.add(Line.heading(t("Cómo se mide", "How it is measured")));
        if (!this.dev) {
            this.lines.add(Line.text(t("Solo observa lo que ya haces: cuándo sale cada colocación y cada golpe, y cuándo llega la respuesta del servidor. No coloca, no golpea y no cambia ningún paquete, así que es seguro en cualquier servidor.",
                    "It only watches what you already do: when each placement and hit leaves, and when the server's answer arrives. It never places, hits or changes a packet, so it is safe on any server.")));
            this.lines.add(Line.text(t("Para comparar dos optimizadores: haz una prueba con uno, cambia de mod, reinicia y repite en el mismo sitio. La pantalla compara la última prueba con la anterior que usó otro optimizador.",
                    "To compare two optimizers: run it with one, swap mods, restart and repeat in the same place. The screen compares the latest run with the previous one that used a different optimizer.")));
            return;
        }
        this.lines.add(Line.note(t("Reloj: System.nanoTime(), monótono. Todas las cifras en ms; percentiles por rango más cercano.",
                "Clock: System.nanoTime(), monotonic. All figures in ms; nearest-rank percentiles.")));
        this.lines.add(Line.note(t("Envío: cabeza de Connection.send en el hilo del juego (ServerboundUseItemOnPacket con cristal en mano, ServerboundAttackPacket/InteractPacket de ataque, ServerboundSetCarriedItemPacket).",
                "Sent: head of Connection.send on the game thread (ServerboundUseItemOnPacket with a crystal in hand, ServerboundAttackPacket/attack InteractPacket, ServerboundSetCarriedItemPacket).")));
        this.lines.add(Line.note(t("[net]: cabeza de ClientPacketListener.handle* en el hilo de red, antes de que PacketUtils lo reprograme. [shown]: el mismo método en el hilo del juego. Las apariciones llegan dentro de ClientboundBundlePacket, cuyo paso de red se recorre buscando ClientboundAddEntityPacket.",
                "[net]: head of ClientPacketListener.handle* on the network thread, before PacketUtils reschedules it. [shown]: the same method on the game thread. Spawns arrive inside ClientboundBundlePacket, whose network pass is scanned for ClientboundAddEntityPacket.")));
        this.lines.add(Line.note(t("Emparejado: aparición ↔ colocación por bloque base (la más antigua pendiente, ventana 3 s); explosión ↔ golpe por centro a menos de 0,25 bloques; retirada ↔ golpe por id de entidad.",
                "Matching: spawn ↔ placement by base block (oldest pending, 3 s window); explosion ↔ hit by centre within 0.25 blocks; removal ↔ hit by entity id.")));
        this.lines.add(Line.note(t("«No dibujado»: el primero entre la ocultación de Crystal Tweaks, un ENTITY_UNLOAD anterior a la retirada del servidor (otro mod lo quitó en local) y la retirada mostrada.",
                "\"Not drawn\": the earliest of Crystal Tweaks' hide, an ENTITY_UNLOAD before the server's removal (another mod removed it locally) and the shown removal.")));
        this.lines.add(Line.note(t("Imagen: intervalo entre pasadas del HUD; huecos ≥ 250 ms descartados (pausas, cargas). Con el HUD oculto (F1) no se cuentan imágenes.",
                "Frame: interval between HUD passes; gaps ≥ 250 ms dropped (pauses, loading). With the HUD hidden (F1) no frames are counted.")));
        this.lines.add(Line.note(t("Sesgo: un golpe o colocación del jugador limita «golpe→siguiente»; compáralo solo entre pruebas del mismo jugador. Ningún paquete se crea, cancela ni retrasa.",
                "Bias: the player's own clicking bounds \"hit→next\"; compare it only between runs by the same player. No packet is created, cancelled or delayed.")));
    }

    // ------------------------------------------------------------------------------------------

    private String ms(double millis) {
        return millis >= 100 ? String.format(Locale.ROOT, "%.0f ms", millis) : String.format(Locale.ROOT, "%.1f ms", millis);
    }

    private String date(long timestamp) {
        return new SimpleDateFormat(this.spanish ? "dd/MM HH:mm" : "MM/dd HH:mm", Locale.ROOT).format(new Date(timestamp));
    }

    private String environment(String environment) {
        return switch (environment) {
            case "singleplayer" -> t("un jugador", "singleplayer");
            case "lan-host" -> t("LAN (anfitrión)", "LAN (host)");
            case "lan" -> "LAN";
            default -> t("servidor", "server");
        };
    }
}
