package org.c4sci.threads;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is intended asynchronous parallel tasks during real-time or interactive processes :
 * <ol> 
 * <li> accepting tasks that are consumed asynchronously by a limited pool of threads</li>
 * <li> while the pool of threads is fully working any new task may be skipped or may block the caller.</li>
 * </ol>
 *There is not guaranty that tasks will be treated or finished in any order.
 * @author jean-marc
 *
 */
public class ProgrammableThread extends Thread {
	private Object 			accessLock = new Object();
	//		private int				publishTaskIndex;
	//		private int 			processTaskIndex;
	private QueuedTask[]	taskQueue;
	//private int 			taskToProcessCount;
	private boolean			shouldStop;
	
	/**
	 * Tasks publishing policies as used by # 
	 * @author jean-marc
	 *
	 */
	public static enum TaskPublishingPolicy {
		/**
		 * Submitting a task while all threads are busy will block the {@link #publishTaskToProcess(int, Object)} caller.
		 */
		BLOCK_ON_PENDING_TASK,
		/**
		 * A task that is send through {@link #publishTaskToProcess(int, Object)} when all threads are busy, will be skipped.
		 */
		SKIP_PENDING_TASK,
		/**
		 * Submitting a task while all threads are busy will raise an unchecked {@link ProgrammableRuntimeException}
		 */
		RAISE_ON_PENDING_TASK
	};

	private class QueuedTask{
		int		taskType;
		Object	taskParameters = null;
		boolean isAvailableForPublishing = true;
		boolean isBeingProcessed = false;
	}

	private Map<Integer, IParametrizedRunnable> taskToProcessorFactory = new ConcurrentHashMap<>();

	/**
	 * Creates a pool of threads that can process tasks through {@link IParametrizedRunnable}
	 * @param task_queue_size Max threads in a pool, that is also the max task count that can be processed in parallel without any special treatment in {@link #publishTaskToProcess(int, Object, int)}
	 * @throws ProgrammablePoolException is the queue size is less than 1.
	 */
	public ProgrammableThread(final int task_queue_size) throws ProgrammablePoolException {
		if (task_queue_size < 1) {
			throw new ProgrammablePoolException("Queue size should be at least 1 but: " + task_queue_size);
		}
		//			publishTaskIndex = 0;
		//			processTaskIndex = 0;
//		taskToProcessCount = 0;
		taskQueue = new QueuedTask[task_queue_size];
		for (int _i=0; _i< taskQueue.length; _i++) {
			taskQueue[_i] = new QueuedTask();
		}
		shouldStop = false;
	}

	/**
	 * Associates a task type with a processor. 
	 * @param task_type_id
	 * @param task_processor_factory A factory to create processor able to process the task type given in parameter
	 * @throws ProgrammablePoolException if the factory is null or an association has already been defined with the task type.
	 */
	public void addProcessor(Integer task_type_id, IParametrizedRunnable task_processor_factory) throws ProgrammablePoolException {
		if (task_processor_factory == null) {
			throw new ProgrammablePoolException("Cannot associate a null processor with task type " + task_type_id);
		}
		if (taskToProcessorFactory.containsKey(task_type_id)) {
			throw new ProgrammablePoolException("Task type " + task_type_id + " has already been published");
		}
		else {
			taskToProcessorFactory.put(task_type_id, task_processor_factory);
		}
	}

	/**
	 * Pushes a task in the thread queue to be processed
	 * @param task_type a value associated with a processor through {@link #addProcessor(Integer, IParametrizedRunnable)}
	 * @param task_parameters if the processor associated with the task type needs parameters, there are bundled here. Otherwise it may be null.
	 * @param publishing_policy What to do if all pool threads are already busy.
	 * @return true if the task will be processed, false otherwise
	 */
	public boolean publishTaskToProcess(final int task_type, final Object task_parameters, final TaskPublishingPolicy publishing_policy) {
		synchronized (accessLock) {
			int _task_index = findAvailableTask();
			if (_task_index < 0) {
				switch (publishing_policy) {
				case SKIP_PENDING_TASK:
					return false;
				case RAISE_ON_PENDING_TASK:
					throw new ProgrammableRuntimeException("Queue is full: cannot accept pending task");
				case BLOCK_ON_PENDING_TASK:
					System.out.println("     ---->blocked on pending task");
					while (_task_index < 0) {
						try {
							accessLock.wait();
						} catch (InterruptedException _e) {
							throw new ProgrammableRuntimeException(_e);
						}
						_task_index = findAvailableTask();
						System.out.println("   ----> waiting on task index = " + _task_index);
					}
					System.out.println("      ----> inserting task in queue");
					insertTaskInQueue(task_type, task_parameters, _task_index);
					accessLock.notify();
					return true;				
				default:
					throw new ProgrammableRuntimeException("Unknown publishing policy: " + publishing_policy.toString());
				}
			}
			else {

				insertTaskInQueue(task_type, task_parameters, _task_index);
				accessLock.notify();
				return true;
			}
		}
	}
	
	private void insertTaskInQueue(final int task_type, final Object task_parameters, final int task_index) {
		taskQueue[task_index].taskType = 					task_type;
		taskQueue[task_index].taskParameters = 				task_parameters;
		taskQueue[task_index].isAvailableForPublishing = 	false;
		taskQueue[task_index].isBeingProcessed = 			false;
//		taskToProcessCount ++;
	}
	
	private int findAvailableTask() {
		for (int _i=0; _i<taskQueue.length; _i++) {
			if (taskQueue[_i].isAvailableForPublishing) {
				return _i;
			}
		}
		return -1;
	}

	public void halt() {
		synchronized (accessLock) {
			shouldStop = true;
			accessLock.notify();
		}
	}

	public void run() {
		while (true) {
			synchronized(accessLock) {
				if (shouldStop) {
					return;
				}
				else {
					int _task_to_process = findTaskToProcess();
					if (_task_to_process == -1) {
						try {
							accessLock.wait();
						} catch (InterruptedException _e) {
							throw new RuntimeException(_e);
						}
					}
					else {						
						IParametrizedRunnable _processor_factory = taskToProcessorFactory.get(taskQueue[_task_to_process].taskType);
						if (_processor_factory == null) {
							throw new RuntimeException("No processor for task type " + taskQueue[_task_to_process].taskType);
						}
						else {
							taskQueue[_task_to_process].isBeingProcessed = true;
							IParametrizedRunnable _processor = _processor_factory.newInstance();
							new Thread() {
								public void run() {
									_processor.run(taskQueue[_task_to_process].taskParameters);
									synchronized (accessLock) {
										taskQueue[_task_to_process].isAvailableForPublishing = true;
//										taskToProcessCount --;
										accessLock.notify();
									}
								}
							}.start();
						}
					}
				}
			}
		}
	}
	
	private int findTaskToProcess() {
		for (int _i=0; _i<taskQueue.length; _i++) {
			if (!taskQueue[_i].isAvailableForPublishing && !taskQueue[_i].isBeingProcessed) {
				return _i;
			}
		}
		return -1;
	}
	
	private class TypeAprocessor implements IParametrizedRunnable{

		private static final int MAX_DURATION_MS = 3000;

		@Override
		public void run() {
			System.out.println("Type A processing...");
			try {
				sleep(new Random().nextInt(MAX_DURATION_MS));
			} catch (InterruptedException _e) {
				throw new RuntimeException(_e);
			}
			System.out.println("Type A processed!");
		}

		@Override
		public Object getParametersBundle() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IParametrizedRunnable newInstance() {
			// TODO Auto-generated method stub
			return new TypeAprocessor();
		}

		@Override
		public boolean needsParametersBundle() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public Class parametersBundleClass() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	private class TypeBprocessor implements IParametrizedRunnable{

		private static final int MAX_DURATION_MS = 4000;

		@Override
		public void run() {
			System.out.println("      Type B processing...");
			try {
				sleep(new Random().nextInt(MAX_DURATION_MS));
			} catch (InterruptedException _e) {
				throw new RuntimeException(_e);
			}
			System.out.println("      Type B processed!");
		}

		@Override
		public Object getParametersBundle() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IParametrizedRunnable newInstance() {
			// TODO Auto-generated method stub
			return new TypeBprocessor();
		}

		@Override
		public boolean needsParametersBundle() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public Class parametersBundleClass() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	public static void main(String[] args) throws InterruptedException {
		int _queue_size = 4;
		int _publishing_count = 10;
		int _task_publish_delay_ms = 500;
		
		int _task_type_a = 0;
		int _task_type_b = 1;

		ProgrammableThread _pool = null;
		try {
			_pool = new ProgrammableThread(_queue_size);
		} catch (ProgrammablePoolException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			System.exit(1);
		}
		try {
			_pool.addProcessor(_task_type_a, _pool.new TypeAprocessor());
			_pool.addProcessor(_task_type_b, _pool.new TypeBprocessor());
		} catch (ProgrammablePoolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(2);
		}
		_pool.start();
		
		Random _random = new Random();
		
		for (int _i=0; _i< _publishing_count; _i++) {
			if (_pool.publishTaskToProcess(_task_type_a, null, TaskPublishingPolicy.BLOCK_ON_PENDING_TASK)) {
				System.out.println("queued type A");
			}
			else {
				System.out.println("skipped type A");
			}
			if (_pool.publishTaskToProcess(_task_type_b, null, TaskPublishingPolicy.BLOCK_ON_PENDING_TASK)) {
				System.out.println("      queued type B");
			}
			else {
				System.out.println("      skipped type B");
			}
			Thread.sleep(_task_publish_delay_ms);
		}
		
		_pool.halt();
		
	}

}
