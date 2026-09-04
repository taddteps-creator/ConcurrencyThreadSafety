import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * เครื่องมือทดลองที่ 4 — ไฟล์นี้ให้มาแล้ว ไม่ต้องแก้ ไม่มีคะแนน
 *
 * คำถามของช่วง C ในคาบบรรยาย: "ถ้าไม่แชร์กันตั้งแต่แรกล่ะ"
 *
 * ไฟล์นี้ทำงานเดียวกับ RaceDemo เป๊ะ ๆ คือให้หลายเธรดฝากเงิน
 * แต่ไม่มีคำว่า synchronized อยู่เลยสักตัว และผลถูกต้องทุกครั้ง
 *
 * เคล็ดลับคือ balance ไม่ได้ถูกแชร์ มันมีเจ้าของอยู่เธรดเดียว
 * เธรดอื่นทำได้อย่างเดียวคือ "ฝากข้อความไว้ในคิว"
 *
 * วิธีรัน:  java ActorDemo
 */
public class ActorDemo {

    private static final int THREADS = 4;
    private static final int OPS_PER_THREAD = 50000;
    private static final int EXPECTED = THREADS * OPS_PER_THREAD;

    /** ข้อความหนึ่งใบที่ส่งเข้าคิว */
    private static class Msg {
        final int amount;
        final boolean poison;   // สัญญาณบอกให้ actor เลิกทำงาน

        Msg(int amount, boolean poison) {
            this.amount = amount;
            this.poison = poison;
        }
    }

    /**
     * เจ้าของสถานะเพียงคนเดียว
     * balance เป็น field ธรรมดา ไม่ final ไม่ volatile ไม่ synchronized
     * ปลอดภัยได้เพราะมีเธรดเดียวเท่านั้นที่แตะมัน
     */
    private static class AccountActor implements Runnable {
        
        private int balance = 0;

        private final BlockingQueue<Msg> inbox = new LinkedBlockingQueue<Msg>();

        /** คนอื่นทำได้แค่นี้ — ฝากข้อความไว้ แล้วจากไป */
        void send(Msg m) throws InterruptedException {
            inbox.put(m);
        }

        /** อ่านยอดได้หลัง actor หยุดทำงานแล้วเท่านั้น */
        int finalBalance() {
            return balance;
        }

        public void run() {
            try {
                while (true) {
                    Msg m = inbox.take();     // ไม่มีของก็รอเอง
                    if (m.poison) {
                        return;
                    }
                    balance = balance + m.amount;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Demo 4: The version with no locks at all ===");
        System.out.println("Exactly the same workload as RaceDemo.");
        System.out.println("But Account is replaced by an actor that solely owns balance.\n");

        final AccountActor actor = new AccountActor();
        Thread actorThread = new Thread(actor, "account-owner");
        actorThread.start();

        Thread[] senders = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            senders[i] = new Thread(new Runnable() {
                public void run() {
                    try {
                        for (int k = 0; k < OPS_PER_THREAD; k++) {
                            actor.send(new Msg(1, false));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        long start = System.currentTimeMillis();

        for (int i = 0; i < THREADS; i++) {
            senders[i].start();
        }
        for (int i = 0; i < THREADS; i++) {
            senders[i].join();
        }

        actor.send(new Msg(0, true));   // บอกให้ actor เลิกงาน
        actorThread.join();

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Actual total : " + actor.finalBalance());
        System.out.println("Expected     : " + EXPECTED);
        System.out.println("Elapsed      : " + elapsed + " ms");
        System.out.println();
        System.out.println(actor.finalBalance() == EXPECTED
                ? "Correct, without a single synchronized keyword."
                : "Wrong (this should not happen).");
        System.out.println();
        System.out.println("Closing question: what does this approach trade away,");
        System.out.println("compared with simply adding locks?");
    }
}
