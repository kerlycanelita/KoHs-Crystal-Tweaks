import com.zymekoh.crystaltweaks.client.OwnershipLedger;
import java.util.HashMap;
import java.util.Map;

/** Whose crystal is whose, on timelines a fight produces. Times are milliseconds on one clock. */
public final class OwnershipLedgerTest {
    private static int checks;
    private static int problems;
    private static final long BASE = 7L;
    private static final long OTHER = 8L;

    private static long ms(long millis) {
        return millis * 1_000_000L;
    }

    private static void check(String what, boolean expected, Boolean actual) {
        checks++;
        if (!Boolean.valueOf(expected).equals(actual)) {
            problems++;
            System.out.println("FAIL: " + what + " (expected " + expected + ", got " + actual + ")");
        }
    }

    /** A ledger that has seen one placement answered in 120 ms, so it knows how fast answers travel. */
    private static OwnershipLedger<Integer> warm(Map<Integer, Boolean> own) {
        OwnershipLedger<Integer> ledger = new OwnershipLedger<>(own);
        ledger.clicked(OTHER, 1, ms(0));
        ledger.appeared(-1, OTHER, ms(100));
        ledger.acknowledged(1, ms(120));
        return ledger;
    }

    public static void main(String[] arguments) {
        Map<Integer, Boolean> own = new HashMap<>();

        // A placement, its crystal, the acknowledgement.
        OwnershipLedger<Integer> ledger = new OwnershipLedger<>(own);
        ledger.clicked(BASE, 1, ms(0));
        check("own crystal shows as own at once", true, ledger.appeared(1, BASE, ms(100)));
        ledger.acknowledged(1, ms(120));
        check("own crystal stays own after the acknowledgement", true, own.get(1));
        check("a crystal on a base never clicked is not own", false, ledger.appeared(2, OTHER, ms(130)));

        // An enemy crystal lands on the base while the click is still travelling.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(1000));
        check("enemy crystal 10 ms after the click is taken for own until the answer", true, ledger.appeared(10, BASE, ms(1010)));
        check("own crystal after it is own", true, ledger.appeared(11, BASE, ms(1100)));
        ledger.acknowledged(2, ms(1120));
        check("own crystal confirmed", true, own.get(11));
        check("enemy crystal stays the enemy's", false, own.get(10));

        // The enemy crystal lands later, when an answer could already have travelled.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(2000));
        check("enemy crystal 80 ms after the click is taken for own for now", true, ledger.appeared(20, BASE, ms(2080)));
        check("own crystal after it", true, ledger.appeared(21, BASE, ms(2100)));
        ledger.acknowledged(2, ms(2120));
        check("the acknowledgement keeps the last one", true, own.get(21));
        check("and passes over the enemy's", false, own.get(20));

        // A click answered with no crystal yet: on a server that answers before it sends the crystal
        // the first crystal on that base is the click's own, and only the first.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(3000));
        ledger.acknowledged(2, ms(3040));
        check("crystal 380 ms after its click was answered is own", true, ledger.appeared(30, BASE, ms(3420)));
        check("the next crystal on that base is not", false, ledger.appeared(31, BASE, ms(3700)));

        // A click the server refused does not stay claimable for long.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(3000));
        ledger.acknowledged(2, ms(3120));
        check("enemy crystal long after a refused click is not own", false, ledger.appeared(32, BASE, ms(4800)));

        // The server from the recording: answers come well before the crystals, the player places and
        // breaks on one base as fast as it goes, with a wasted click in every round.
        own.clear();
        ledger = warm(own);
        for (int round = 0; round < 8; round++) {
            long at = 20_000 + round * 220L;
            ledger.clicked(BASE, 10 + round * 2, ms(at));
            ledger.acknowledged(10 + round * 2, ms(at + 30));
            ledger.clicked(BASE, 11 + round * 2, ms(at + 50));
            ledger.acknowledged(11 + round * 2, ms(at + 80));
            check("burst round " + round + ": own crystal after an early answer", true,
                    ledger.appeared(200 + round, BASE, ms(at + 120)));
        }
        check("enemy crystal after the burst is not own", false, ledger.appeared(299, BASE, ms(20_000 + 8 * 220L + 400)));

        // The same server, the crystal arriving while a later click is still unanswered.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(30_000));
        ledger.acknowledged(2, ms(30_030));
        ledger.clicked(BASE, 3, ms(30_100));
        check("own crystal while a later click waits", true, ledger.appeared(300, BASE, ms(30_110)));
        ledger.acknowledged(3, ms(30_130));
        check("and it stays own once that click is answered", true, own.get(300));

        // Two clicks handled in one server tick, a crystal each.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(4000));
        ledger.clicked(BASE, 3, ms(4050));
        ledger.appeared(40, BASE, ms(4100));
        ledger.appeared(41, BASE, ms(4101));
        ledger.acknowledged(3, ms(4120));
        check("first of two crystals in one tick", true, own.get(40));
        check("second of two crystals in one tick", true, own.get(41));

        // Spam: five clicks, one crystal; the rest were refused and must not claim the enemy's next crystal.
        own.clear();
        ledger = warm(own);
        for (int click = 0; click < 5; click++) {
            ledger.clicked(BASE, 2 + click, ms(5000 + click * 20));
        }
        ledger.appeared(50, BASE, ms(5100));
        ledger.acknowledged(2, ms(5120));
        ledger.acknowledged(6, ms(5170));
        check("the one crystal of a spam is own", true, own.get(50));
        check("enemy crystal after the spam is not own", false, ledger.appeared(51, BASE, ms(5600)));

        // A server that never acknowledges: the old rule, the next crystal on a clicked base.
        own.clear();
        ledger = new OwnershipLedger<>(own);
        ledger.clicked(BASE, 1, ms(6000));
        check("without acknowledgements the crystal is own", true, ledger.appeared(60, BASE, ms(6100)));
        ledger.settle(ms(12000));
        check("and stays own", true, own.get(60));

        // A server that acknowledges first and sends the crystal after, in the same tick.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(7000));
        ledger.acknowledged(2, ms(7100));
        check("crystal right after its acknowledgement is own", true, ledger.appeared(70, BASE, ms(7110)));
        check("a later crystal on that base is not", false, ledger.appeared(71, BASE, ms(7400)));

        // An answer quicker than any before it: the player's own crystal all the same.
        own.clear();
        ledger = warm(own);
        ledger.clicked(BASE, 2, ms(7600));
        check("own crystal 5 ms after the click is own", true, ledger.appeared(75, BASE, ms(7605)));
        ledger.acknowledged(2, ms(7606));
        check("and stays own once acknowledged", true, own.get(75));

        // Place, break, place again on the same base, each answered in turn.
        own.clear();
        ledger = warm(own);
        for (int round = 0; round < 6; round++) {
            long at = 8000 + round * 150L;
            ledger.clicked(BASE, 2 + round, ms(at));
            check("round " + round + " crystal is own", true, ledger.appeared(80 + round, BASE, ms(at + 100)));
            ledger.acknowledged(2 + round, ms(at + 120));
            check("round " + round + " crystal confirmed", true, own.get(80 + round));
        }

        System.out.println("ownership checks: " + checks + "  problems: " + problems);
        if (problems > 0) {
            System.exit(1);
        }
    }
}
