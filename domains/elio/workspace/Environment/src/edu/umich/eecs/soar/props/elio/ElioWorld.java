package edu.umich.eecs.soar.props.elio;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sml.Identifier;
import sml.Kernel;
import sml.Kernel.UpdateEventInterface;
import sml.WMElement;
import sml.smlUpdateEventId;
import sml.Agent;
import edu.umich.soar.debugger.SWTApplication;

//import sml.Agent.RunEventInterface;
//import sml.smlRunEventId;

public class ElioWorld implements UpdateEventInterface {
	private static final boolean USING_PROPS = true;
	private static final boolean VERBOSE = true;
	private static boolean COND_SPREAD = true;
	private static boolean TESTING = false;
	private static String PROJ_DIR;
	private static String PROPS_DIR;
	
	private static final String AGENT_NAME = "ElioAgent";
	private static int agent_num = 0,
						current_sample = 0,
						currentPropsThreshold = 10,
						lastDC = 0;
	final protected String CMD_SAY = "say",
							CMD_FINISH = "finish",
							CMD_INSTR_NEXT = "props-command";
	protected String outFileName = "elio_props.dat",
					currentTaskName,
					currentLearnMode = "c23";
	private Identifier input_link;
	private Kernel kernel;
	private Agent agent = null;
	private WMElement in1, 
					  in2,
					  inTask,
					  inTaskSeqName;

	private ETask etask;
	private List<Result> results;
	
	
	// We'll use the same numbers on the screen over and over again. The model will not notice.
	private static final Map<String, Integer> inputs;
	static {
		Map<String, Integer> aMap = new HashMap<String, Integer>();
		aMap.put("solid", 6);
		aMap.put("algae", 2);
		aMap.put("lime1", 3);
		aMap.put("lime2", 5);
		aMap.put("lime3", 1);
		aMap.put("lime4", 9);
		aMap.put("limemax", 2);
		aMap.put("limemin", 1);
		aMap.put("toxin1", 4);
		aMap.put("toxin2", 8);
		aMap.put("toxin3", 7);
		aMap.put("toxin4", 2);
		aMap.put("toxinmin", 2);
		aMap.put("toxinmax", 8);
		inputs = Collections.unmodifiableMap(aMap);
	}

	
	ElioWorld(Kernel kernel) {
		PROJ_DIR = "/home/bryan/Dropbox/UM_misc/Soar/Research/PROPs/PRIMs_Duplications/Elio/";
		PROPS_DIR = "/home/bryan/Dropbox/UM_misc/Soar/Research/PROPs/PROPs Project/";
		
		this.kernel = kernel;
		this.kernel.RegisterForUpdateEvent(smlUpdateEventId.smlEVENT_AFTER_ALL_OUTPUT_PHASES, this, this);
	}
	
	private void loadLearnProductions(String mode) {
		if (COND_SPREAD && !mode.contains("c")) {
			agent.LoadProductions(PROJ_DIR + "prims_elio02_condspread_chunks.soar");
		}
		if (mode.contains("3only")) {
			agent.LoadProductions(PROPS_DIR + "props_learn_l3only.soar");
			return;
		}
		
		if (!mode.contains("1")) {
			agent.LoadProductions(PROJ_DIR + "prims_elio01_L1-chunks.soar");
		}
		if (mode.contains("2")) {
			agent.LoadProductions(PROPS_DIR + "props_learn_l2.soar");
		}
		if (mode.contains("3")) {
			if (mode.contains("2"))
				agent.LoadProductions(PROPS_DIR + "props_learn_l3.soar");
			else
				agent.LoadProductions(PROPS_DIR + "props_learn_l3only.soar");
		}
	}
	
	private void createAgent() {
		if (agent != null) {
			if (VERBOSE)
				agent.ExecuteCommandLine("clog -c");
			kernel.DestroyAgent(agent);
		}
		
		agent = kernel.CreateAgent(AGENT_NAME + (agent_num++));
		
		input_link = agent.GetInputLink();
		in1 = null;
		in2 = null;
		inTask = null;
		inTaskSeqName = null;
		

		// Load the agent files
		if (USING_PROPS) {
			agent.LoadProductions(PROPS_DIR + "_firstload_props.soar");			// props library
			
			agent.LoadProductions(PROJ_DIR + "prims_elio01_agent_smem.soar");		// elio props instructions
			agent.LoadProductions(PROJ_DIR + "prims_elio_procedures_smem.soar");	// manual instruction sequences for tasks A-D
			
			if (COND_SPREAD)
				agent.LoadProductions(PROPS_DIR + "props_learn_conds.soar");

			agent.ExecuteCommandLine("chunk confidence-threshold " + currentPropsThreshold);

		}
		else {
			agent.LoadProductions(PROJ_DIR + "prims_elio01_agent.soar");			// elio agent to be learned (for testing)
		}
		
		agent.LoadProductions(PROJ_DIR + "lib_actr_interface.soar");		// actr memory interface
		agent.LoadProductions(PROJ_DIR + "smem_elio.soar");					// smem memory used by elio agent

		loadLearnProductions(currentLearnMode);
		
		etask = new ETask(1,1,current_sample);
		results = new ArrayList<Result>();
		
		lastDC = 0;
		
		//agent.ExecuteCommandLine("save percepts -o ElioAgentPercepts.spr -f");
		//agent.ExecuteCommandLine("save agent SavedElioAgent.soar");

		if (VERBOSE) {
			agent.ExecuteCommandLine("watch 1");
			agent.ExecuteCommandLine("watch --learn 1");
			agent.ExecuteCommandLine("clog -A verbose_"+outFileName);
		}
		else if (!TESTING){
			agent.ExecuteCommandLine("watch 0");
			agent.ExecuteCommandLine("output enabled off");
			agent.ExecuteCommandLine("output agent-writes off");
		}
		
	}
	
	public void setOutputFile(String filename) {
		outFileName = filename;
	}
	
	public void initTask(String task) {
		currentTaskName = task;
		
		// Clear input if any
		if (in1 != null) {
			in1.DestroyWME();
			in1 = null;
		}
		if (in2 != null) {
			in2.DestroyWME();
			in2 = null;
		}
		if (inTask != null) {
			inTask.DestroyWME();
			inTask = null;
		}
		if (inTaskSeqName != null) {
			inTaskSeqName.DestroyWME();
			inTaskSeqName = null;
		}
		
		// Clear results
		results.clear();
		// Init task
		etask.init();
		inTask = input_link.CreateStringWME("task", task);
		inTaskSeqName = input_link.CreateStringWME("task-sequence-name", task);
		agent.Commit();
	}
	
	public void initManualSequence(String task) {
		String s;
		if (task != null)
			s = task.toLowerCase();
		else
			s = currentTaskName;
		
		if (s.compareTo("procedure-a") == 0) {
			agent.LoadProductions(PROJ_DIR + "prims_elio_procedureA_smem.soar");
		}
		else if (s.compareTo("procedure-b") == 0) {
			agent.LoadProductions(PROJ_DIR + "prims_elio_procedureB_smem.soar");
		}
		else if (s.compareTo("procedure-c") == 0) {
			agent.LoadProductions(PROJ_DIR + "prims_elio_procedureC_smem.soar");
		}
		else if (s.compareTo("procedure-d") == 0) {
			agent.LoadProductions(PROJ_DIR + "prims_elio_procedureD_smem.soar");
		}
	}
	
	// Test for number of incorrect instruction retrievals, sweeping over certain (spreading) parameters
	public void runQueryTest(int steps) {
		TESTING = true;
		List<String> values = new ArrayList<>(Arrays.asList("0.1","0.3","0.5","0.7","0.9"));
		List<String> tasks = new ArrayList<>(Arrays.asList("procedure-a"));
		String command = "smem --set base-decay";
		
		for (String value : values) {
			outFileName = "elio_props_param_test_decay" + value + ".dat";
			createAgent();
			clearOutputFile();
			agent.ExecuteCommandLine(command + " " + value);
			agent.ExecuteCommandLine("watch 0");

			for (String task : tasks) {
				initTask(task);

				agent.RunSelf(steps);

				printResults(task);
			}
		}

	}
	public void runTest(int trials) {
		TESTING = true;
		outFileName = "elio_props_test.dat";
		createAgent();
		clearOutputFile();
		
		List<String> tasks = new ArrayList<>(Arrays.asList("procedure-a", "procedure-b", "procedure-c", "procedure-d"));
		for (String task : tasks) {
			initTask(task);

			for (int i=0; i<trials; ++i) {
				agent.RunSelfForever();
			}
			
			printResults(task);
		}
		
		agent.ExecuteCommandLine("p -fc");
		
	}
	public void runTest(int trials, int steps, String task) {
		TESTING = true;
		outFileName = "elio_props_test.dat";
		createAgent();
		clearOutputFile();
		initTask(task);
		
		for (int i=0; i<trials; ++i) {
			if (steps <= 0) {
				agent.RunSelfForever();		// until receiving the finish command
			}
			else {
				agent.ExecuteCommandLine("watch 1");
				agent.ExecuteCommandLine("output console on");
				agent.RunSelf(steps);
			}

		}

		agent.ExecuteCommandLine("clog -c");
		
		printResults(task);
		
		//agent.ExecuteCommandLine("save agent ElioAgent_post.soar");
	}
	
	public void runDebug() {
		TESTING = true;
		createAgent();
		initTask("procedure-a");
		
		//agent.ExecuteCommandLine("output console off");
		try {
			SWTApplication swtApp = new SWTApplication();
			swtApp.startApp(new String[]{"-remote"});

			agent.KillDebugger();
			System.exit(0);
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	public void doLevelThresholdSweep(String exp_name, int samples) {
		List<String> levelTrials = new ArrayList<>(Arrays.asList("c12"));//, "2","c3only","c23"));
		List<Integer> thresholds = new ArrayList<>(Arrays.asList(10));//1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25));
		
		if (COND_SPREAD)
			exp_name = exp_name+"_sc";
		
		// For each parameter combination
		for (String l : levelTrials) {
			currentLearnMode = l;
			for (int t : thresholds) {
				currentPropsThreshold = t;
				setOutputFile(exp_name + "_l" + l + "_t" + t + ".dat");
				doExperiment(samples, 50);
			}
			System.out.println("\nCOMPLETED MODE " + l);
		}
		
	}
	
	public void doThresholdExperiment(String exp_name, int samples) {
		List<Integer> procedureTrials = new ArrayList<>(Arrays.asList(/*2, 5, 10, 20, 30, 40, */50));
		// NOTE: These integers must correspond to valid filename affixes:
		List<Integer> thresholds = new ArrayList<>(Arrays.asList(/*1, 2, 4, 8,*/ 12/*, 16, 20, 24, 28, 32*/));
		
		// For each parameter combination
		for (int l : procedureTrials) {
			for (int i : thresholds) {
				currentPropsThreshold = i;
				setOutputFile(exp_name + "_t" + i + "l" + l + ".dat");
				doExperiment(samples, l);
			}
		}
		
	}
	
	public void doExperiment(int samples, int trials) {
		clearOutputFile();
		
		for (int i=0; i<samples; ++i) {
			current_sample = i;
			doElio(trials);
			System.out.println("\nCompleted sample " + i);
		}
		
		if (VERBOSE)
			agent.ExecuteCommandLine("clog -c");
		
		System.out.println("\nDONE!");
	}
	
	public void doElio(int num_trials) {
		List<String> tasks = new ArrayList<>(Arrays.asList("procedure-b", "procedure-c", "procedure-d"));

		// Alternate num_trials trials of procedure-A with num_trials trials of each of the others
		for (String task : tasks) {
			createAgent();
			
			initTask("procedure-a");
			for (int i=0; i<num_trials; ++i) {
				agent.RunSelfForever();	// Run until receiving the finish command
			}
			printResults("procedure-a");
			System.out.println("\nCompleted PROCEDURE A");

			initTask(task);
			for (int i=0; i<num_trials; ++i) {
				agent.RunSelfForever();	// Run until receiving the finish command
			}
			printResults(task.toUpperCase());
			System.out.println("\nCompleted " + task);
			
		}
		
		if (VERBOSE)
			agent.ExecuteCommandLine("clog -c");
	}
	
	public void doElioTight(int samples, int repeats, List<Integer> interleaves, List<Integer> thresholds) {
		
		List<String> tasks = new ArrayList<>(Arrays.asList("procedure-b", "procedure-c", "procedure-d"));

		for (int interleave : interleaves) {
			int reps = repeats / interleave;	// Assume it divides evenly
			
			// Alternate procedure-A with one of the others, for a given number of trials
			// For each parameter combination
			for (int t : thresholds) {
				currentPropsThreshold = t;
	
				for (int j=0; j<tasks.size(); ++j) {
					setOutputFile("elio_props_tight" + "_t" + t + "l" + repeats + "i" + interleave + ".dat");
					for (int s=0; s<samples; ++s) {
						createAgent();
						for (int r=0; r<reps; ++r) {
							for (int i=0; i<interleave; ++i) {
								initTask("procedure-a");
								agent.RunSelfForever();	// Run until receiving the finish command
								printResults("procedure-a");
							}
	
							for (int i=0; i<interleave; ++i) {
								initTask(tasks.get(j));
								agent.RunSelfForever();	// Run until receiving the finish command
								printResults(tasks.get(j).toUpperCase());
							}
						}
					}
				}
			}
		}

	}
	
	// Do each procedure individually
	public void doElioSingles(int samples) {
		List<Integer> procedureTrials = new ArrayList<>(Arrays.asList(50));
		List<Integer> thresholds = new ArrayList<>(Arrays.asList(24/*2, 4, 8, 16, 32, 64*/));

		List<String> tasks = new ArrayList<>(Arrays.asList("procedure-a", "procedure-b", "procedure-c", "procedure-d"));
		// For each parameter combination
		for (int l : procedureTrials) {
			for (int t : thresholds) {
				currentPropsThreshold = t;
				for (int j=0; j<tasks.size(); ++j) {
					setOutputFile("elio_props_proc" + (char)('A'+j) + "_t" + t + "l" + l + ".dat");
					for (int s=0; s<samples; ++s) {
						createAgent();
						initTask(tasks.get(j));
						for (int k=0; k<l; ++k) {
							agent.RunSelfForever();	// Run until receiving the finish command
						}
						printResults(tasks.get(j).toUpperCase());
					}
				}
			}
		}
		System.out.println("DONE!");
		
	}

	// Clear the output file if it already exists
	public boolean clearOutputFile() {
		if (!VERBOSE) {
			try {
				PrintWriter writer;
				writer = new PrintWriter(outFileName);
				writer.print("");
				writer.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}

	public void printResults(String task) {
		if (VERBOSE) {
			for (Result r : results) {
				agent.ExecuteCommandLine("echo " + task + "\t" + r.toString());
			}
		}
		else {
			try(FileWriter fw = new FileWriter(outFileName, true);
					BufferedWriter bw = new BufferedWriter(fw);
					PrintWriter out = new PrintWriter(bw))
			{
				for (Result r : results) {
					out.println(task + "\t" + r.toString());
				}
			}
			catch (IOException e) {
				System.err.println("ERROR: Unable to append to file '" + outFileName + "'");
			}
		}
	}
	
	@Override
	public void updateEventHandler(int arg0, Object data, Kernel kernel, int arg3) {
		
		int C = agent.GetNumberCommands();
		if (C <= 0) {
			return;
		}
		
		// Clear old input
		if (in1 != null) {
			in1.DestroyWME();
			in1 = null;
		}
		if (in2 != null) {
			in2.DestroyWME();
			in2 = null;
		}
		
		boolean stopAgent = false;
		for (int c = 0; c < C; ++c) {
			final Identifier id = agent.GetCommand(c);

			if (id != null) {

				int nChildren = id.GetNumberChildren();
				
				// Handle Elio commands
				if (id.GetCommandName().compareTo(CMD_SAY) == 0 && nChildren == 2) {
					// Get the output
					String val1 = id.GetParameterValue("out1");
					String val2 = id.GetParameterValue("out2");

					// Generate the corresponding input
					int input2;
					if (val1.compareTo("read") == 0) {
						input2 = inputs.get(val2);
						in1 = input_link.CreateStringWME("in1", val2);
						in2 = input_link.CreateIntWME("in2", input2);
					}
					else if (val1.compareTo("enter") == 0) {
						// (Inputs already destroyed, leave it that way)
						// Get the current number of chunks
						int chunkCount = agent.ExecuteCommandLine("p -c").split("\n").length;
						// Store the result
						int DC = agent.GetDecisionCycleCounter();
						results.add(new Result(etask, val2, DC - lastDC, chunkCount));
						lastDC = DC;
						// Start next task
						etask.line++;
						etask.start = System.nanoTime();
						if (VERBOSE)
							System.out.println("# Enter " + val2 + " #");
					}

					id.AddStatusComplete();

					//System.out.println(val1 + " " + val2);
				}
				else if (id.GetCommandName().compareTo(CMD_FINISH) == 0) {
					stopAgent = true;
				}
				else {
					id.AddStatusError();
				}

			}
		}
		

		if (stopAgent) {
			agent.StopSelf();

			// Reset for next trial
			etask.line = 1;
			etask.trial++;
			etask.start = System.nanoTime();
		}
		
		agent.Commit();

	}

}