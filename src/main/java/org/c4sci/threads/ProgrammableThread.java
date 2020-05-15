package org.c4sci.threads;

public class ProgrammableThread extends Thread {

	private Integer         threadLock = 		Integer.valueOf(0);
	private boolean         aTaskIsSubmitted = false;
	private Runnable        taskToProcess =		null;
	private boolean         shoudlStop =		false;

	public enum ThreadPolicy{
		SKIP_PENDING,
		WAIT_PENDING
	}

	/**
	 * Tries to make a task to be processed.
	 * @param to_be_processed The task to process.
	 * @param thread_policy Whether the caller waits for the task to be taken into account or gets its task skipped.
	 * @return true if the task will be processed, false otherwise. 
	 */
	public boolean submitTask(Runnable to_be_processed, final ThreadPolicy thread_policy){
		synchronized (threadLock){
			if (shoudlStop) {
				threadLock.notify();
				return false;
			}
			if (aTaskIsSubmitted){
				switch (thread_policy){
				case SKIP_PENDING:
					return false;
				case WAIT_PENDING:
					while(aTaskIsSubmitted){
						try {
							threadLock.wait();							
						} catch (InterruptedException _e) {
							throw new ProgrammableThreadingException("setupContext interrupted", _e);
						}
					}
					aTaskIsSubmitted = true;
					taskToProcess = to_be_processed;
					threadLock.notify();
					return true;
				default:
					throw new ProgrammableThreadingException("Uncovered case :" + thread_policy.toString());
				}
			}
			else {
				aTaskIsSubmitted = true;
				taskToProcess = to_be_processed;
				threadLock.notify();
				return true;
			}
		}
	}

	@Override
	public void run(){
		while(!shoudlStop){
			if (aTaskIsSubmitted){
				taskToProcess.run();
				synchronized (threadLock) {
					aTaskIsSubmitted = false;
					taskToProcess = null;
					threadLock.notify();
				}
			}
			else{
				try {
					synchronized (threadLock) {
						threadLock.wait();
					}
				} catch (InterruptedException _e) {
					throw new ProgrammableThreadingException("Interrupted", _e);
				}
			}

		}
	}

	public void halt(){
		synchronized (threadLock) {
			shoudlStop = true;
			threadLock.notify();
		}
	}

	public void waitForThreadFinished() {
		synchronized (threadLock) {
			while (aTaskIsSubmitted) {
				try {
					threadLock.wait();
				} catch (InterruptedException _e) {
					throw new ProgrammableThreadingException(_e);
				}
			}
			threadLock.notify();			
		}
	}

	public static void main(String[] args) {
		int _publish_count = 5;
		int _process_delay_ms = 1000;
		int _process_duration_ms = 2200;

		ProgrammableThread _thread = new ProgrammableThread();
		_thread.start();

		for (int _i=0; _i<_publish_count; _i++) {
			System.out.println("Submitting task #" + _i);
			if (_thread.submitTask(
					()-> {
						try {
							System.out.println("             Processing ...");
							Thread.sleep(_process_duration_ms);
							System.out.println("             processed !");
						} catch (InterruptedException _e) {
							throw new RuntimeException("Processing interrupted", _e);
						}
					},
					ThreadPolicy.SKIP_PENDING)) {
				System.out.println(" .... send");
			}
			else {
				System.out.println(".... skipped");
			}
			try {
				Thread.sleep(_process_delay_ms);
			} catch (InterruptedException _e) {
				throw new RuntimeException("Submitting interrupted", _e);
			}
		}

		System.out.println("Halting and waiting for finished...");
		_thread.halt();
		_thread.waitForThreadFinished();
		System.out.println("Done");

	}

}
