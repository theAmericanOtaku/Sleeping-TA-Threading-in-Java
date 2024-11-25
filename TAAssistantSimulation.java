import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TAAssistantSimulation {

    private static final int MAX_CHAIRS = 3;
    private static final int TOTAL_STUDENTS = 10;
    private static final int MAX_RETRIES = 3;
    private static boolean ENDOFPROGRAM = false;

    private int waitingList = 0;
    private final Queue<Integer> appointments = new LinkedList<>();
    private final Lock lock = new ReentrantLock();
    private final Condition condWakeup = lock.newCondition();
    private Semaphore chairSemaphore = new Semaphore(MAX_CHAIRS, true);

    class TeachingAssistant implements Runnable {
        public void run() {
            System.out.println("[TA THREAD] TA is in the office.");

            try {
                while (true) {
                    lock.lock();
                    try {
                        // TA sleeps if no students or appointments are waiting
                        while (waitingList == 0 && appointments.isEmpty() && !ENDOFPROGRAM) {
                            System.out.println("[TA THREAD] TA is sleeping.");
                            condWakeup.await();
                        }

                        // Break the loop and end if the program should end and no students are being helped
                        if (ENDOFPROGRAM && waitingList == 0 && appointments.isEmpty()) break;

                        // Serve students with appointments first
                        if (!appointments.isEmpty()) {
                            int studentId = appointments.poll();
                            System.out.println("[TA THREAD] TA is helping student " + studentId + " with an appointment.");
                        } else if (waitingList > 0) {
                            // Help the next waiting student from the hallway
                            System.out.println("[TA THREAD] TA is helping a student from the hallway.");
                            waitingList--;
                            chairSemaphore.release();
                        }

                        // Status after helping a student
                        System.out.println("[TA THREAD] TA is done with the student!");
                        System.out.println("[TA THREAD] Waiting students = " + waitingList + ", Student in the office = " + (waitingList > 0 || !appointments.isEmpty() ? 1 : 0));
                    } finally {
                        lock.unlock();
                    }

                    // Simulate time helping a student
                    Thread.sleep((long) (Math.random() * 2000) + 1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("[TA THREAD] TA has left the office.");
        }
    }

    class Student implements Runnable {
        private final int id;

        public Student(int id) {
            this.id = id;
        }

        public void run() {
            int attempts = 0;

            while (!ENDOFPROGRAM && attempts < MAX_RETRIES) {
                lock.lock();
                try {
                    System.out.println("[SEMAPHORE] Student " + id + " gets the lock!");
                    System.out.println("[STUDENT THREAD] Student " + id + " is coming!");

                    if (chairSemaphore.tryAcquire()) {
                        waitingList++;
                        int chairNumber = MAX_CHAIRS - waitingList + 1;
                        System.out.println("[STUDENT THREAD] Student " + id + " is waiting.");
                        System.out.println("[STUDENT THREAD] Student " + id + " is seating on the waiting chair #" + chairNumber + ".");
                        System.out.println("[STUDENT THREAD] Waiting students = " + waitingList + ", Student in the office = 1");
                        condWakeup.signal(); // Wake up TA if sleeping
                        return; // Student is now waiting for help, so exit loop
                    } else {
                        System.out.println("[STUDENT THREAD] Student " + id + " finds all chairs taken and leaves.");
                        attempts++;
                    }
                } finally {
                    System.out.println("[SEMAPHORE] Student " + id + " releases the lock!");
                    lock.unlock();
                }

                // Wait a random time before retrying
                try {
                    Thread.sleep((long) (Math.random() * 2000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Make an appointment after 3 unsuccessful attempts
            if (attempts >= MAX_RETRIES) {
                lock.lock();
                try {
                    appointments.offer(id);
                    System.out.println("[STUDENT THREAD] Student " + id + " makes an appointment and will come back later.");
                    condWakeup.signal(); // Notify TA about the appointment and wake up if sleeping
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    public void start() {
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_STUDENTS + 1);

        // Start TA thread
        TeachingAssistant ta = new TeachingAssistant();
        executor.execute(ta);

        // Start student threads
        for (int i = 0; i < TOTAL_STUDENTS; i++) {
            Student student = new Student(i + 1);
            executor.execute(student);
            try {
                Thread.sleep((long) (Math.random() * 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // End simulation and signal the TA to finish
        lock.lock();
        try {
            ENDOFPROGRAM = true;
            condWakeup.signal(); // Wake up TA to finish
        } finally {
            lock.unlock();
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            // Wait for all threads to finish
        }
        System.out.println("All threads have finished.");
    }

    public static void main(String[] args) {
        new TAAssistantSimulation().start();
    }
}













