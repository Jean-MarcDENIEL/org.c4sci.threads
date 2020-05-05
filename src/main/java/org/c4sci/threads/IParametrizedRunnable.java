/**
 * 
 */
package org.c4sci.threads;

/**
 * This class encapsulates Runnable with the ability to run with a parameters bundle
 * @author jean-marc
 *
 */
public interface IParametrizedRunnable extends Runnable {
	Object getParametersBundle();
	IParametrizedRunnable newInstance();	
	
	/**
	 * Processes a task
	 * @param parameters_bundle The parameters bundle necessary to the task. Its class shoudl derive from {@link #parametersBundleClass()}. It may be null if {@link #needsParametersBundle()} returns false.
	 */
	default void run(Object parameters_bundle) {
		if (parameters_bundle == null) {
			run();
		}
	}
	/**
	 * Indicates whereas the {@link #run(Object)} treatment done by this class can accept a null parameters bundle. 
	 * @return
	 */
	boolean needsParametersBundle();
	/**
	 * Returns the type of parameters bundle, or null if no parameters are necessary.
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	Class parametersBundleClass();

}
