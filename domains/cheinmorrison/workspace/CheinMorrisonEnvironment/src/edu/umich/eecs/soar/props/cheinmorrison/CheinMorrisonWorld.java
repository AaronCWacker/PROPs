package edu.umich.eecs.soar.props.cheinmorrison;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.umich.eecs.soar.propsutil.LearnConfig;
import edu.umich.eecs.soar.propsutil.PROPsEnvironment;


public class CheinMorrisonWorld extends PROPsEnvironment {
	//private static double STD_MOTOR_TIME = 0.25,
	//					  STD_VISUAL_TIME = 0.25;

	//private Report rep;
	private VCWM cstask;
	private Stroop sttask;

	private String taskMode = "";
	private boolean inDebug = false;
	private int numSamples = 1;
	
	CheinMorrisonWorld() {
		String proj_dir = "/home/bryan/Documents/GitHub_Bryan-Stearns/PROPs/domains/cheinmorrison/";
		String props_dir = "/home/bryan/Documents/GitHub_Bryan-Stearns/PROPs/PROPsAgent/";
		
		this.setAgentName("CheinMorrisonAgent");
		this.setPropsDir(props_dir);
		
		//this.setCondChunkFile(proj_dir + "cheinNR_agent_condspread_chunks.soar");
		//this.setAddressChunkFile(proj_dir + "cheinNR_agent_L1_chunks.soar");
		//this.setFetchSeqFile(proj_dir + "cheinNR_agent_fetch_procedures.soar");
		this.setInstructionsFile(proj_dir + "cheinNR_agent_instructions.soar");
		this.setSoarAgentFile(proj_dir + "cheinNR_agent.soar");
		
		this.setIOSize(3, 2);

		this.setUserAgentFiles(Arrays.asList("/home/bryan/Documents/GitHub_Bryan-Stearns/PROPs/domains/lib_actransfer_interface.soar", 
												proj_dir + "chein_agent_smem.soar"));
		
		cstask = new VCWM();
		sttask = new Stroop();
	}
	
	public void runCheinDebug(String task, int threshold, String mode) {
		inDebug = true;
		taskMode = task;
		this.runDebug(task, task, threshold, mode);
		inDebug = false;
	}
	
	// Get a random integer within [0,max)
	private int get_rand_int(int max) {
		return (int)(Math.random()*(max));
	}
	private String get_rand_word() {
		List<String> words = Arrays.asList("umbrella","tantrum","xobos","fartnot");
		return words.get(get_rand_int(words.size()));
	}
	// Return a random letter within [a-j]
	private String get_rand_letter() {
		//List<String> words = Arrays.asList("a","b","c","d","e","f","g","h","i","j");
		return Character.toString((char)(get_rand_int((int)('h'-'a'))+'a'));
	}
	
	private void set_perception(String in1, String in2, String in3) {
		this.setPerception(Arrays.asList(in1, in2, in3));
	}
	
	private double VCWM_action(String action, String out1) {
		double latency = 0.05;
		
		if (action.equals("type") && cstask.state.equals("lexical") && (this.getElapsedTime() - cstask.start) < secToMilli(4.0)) {
			// 4 seconds are not yet up
			set_perception("pending", null, null);	// CUSTOM: acknowledge the agent's response
			this.scheduleInput(0.5, Arrays.asList("word", get_rand_word()));	// 0.5
		} else if (action.equals("type") && cstask.state.equals("lexical")) {
			// 4 seconds are up
			// trigger_reward(TASK_REWARD);
			String next_letter = get_rand_letter();
			if (cstask.count == 0) {
				cstask.state = "report";
				this.scheduleInput(1.135, Arrays.asList("report","")); // last letter, schedule a report	// 1.135
			} else {
				cstask.count--;
				cstask.start = this.getElapsedTime() + this.secToMilli(1.635);
				this.scheduleInput(1.135, Arrays.asList("word", get_rand_word()));	// 1.135
			}
			set_perception("letter", next_letter, "");
			cstask.stimuli.add(0, next_letter);
			latency = 0.135;
		} else if (action.equals("type") && cstask.state.equals("report")) {
			cstask.responses.add(0, out1);
			set_perception("report", "", "");
			latency = 0.2;
		} else if (action.equals("enter")) {
			//trigger_reward(TASK_REWARD);
			Boolean correct = cstask.responses.equals(cstask.stimuli);
			this.addReport(String.format("%1d %2d", cstask.current_span, correct ? 1 : 0));
			if (cstask.last_correct != null && cstask.current_span > 1) {
				if (correct && cstask.last_correct) {
					cstask.current_span++;
					correct = null;
				} else if (!correct && !cstask.last_correct) {
					cstask.current_span--;
					correct = null;
				}
			}
			cstask.last_correct = correct;
			cstask.state = "lexical";
			this.clearPerception();
			cstask.trials--;
			// Done with entering the stimuli
			// Handle this later. Let's test this stuff first.
			//System.out.println("Enter has been pushed, current span " + cstask.current_span);
			//System.out.println(cstask.toString());
			
			// Set up the next trial
			if (cstask.trials > 0) {
				cstask.responses.clear();
				cstask.stimuli.clear();
				cstask.count = cstask.current_span - 1;		// keep a counter
				cstask.start = this.getElapsedTime() + this.secToMilli(0.365);
				this.scheduleInput(0.5, Arrays.asList("word", get_rand_word()));
			}
		} else if (action.equals("wait")) {
			set_perception("pending", null, null);
		}
		
		return latency;
		
	}
	
	private double stroop_action(String action, String out1) {
		double latency = 0.05;
		
		if (action.equals("get-property")) {
			if (sttask.count % 2 == 0) {
				sttask.type = "congruent";
				sttask.answer = "red-concept";
				set_perception("rred","red-color","red-word");
			} else {
				sttask.type = "incongruent";
				sttask.answer = "red-concept";
				set_perception("rblue","red-color","blue-word");
			}
			
			if (out1.equals("color-property")) {
				set_perception("rblue","red-color", null);
			}
			
			latency = 0.2;
		} else if (action.equals("say")) {
			latency = 0.2;
			
			Boolean correct = sttask.answer.equals(out1);
			//trigger_reward( (correct) ? TASK_REWARD : 0.0 );
			this.addReport(String.format("%d \t%s \t%d \t%.3f", 
					sttask.count+1, sttask.type.toUpperCase(), correct ? 1 : 0, this.milliToSec(this.getElapsedTime()+this.secToMilli(0.2)-sttask.starttime)));
			
			String new_percept = (++sttask.count > sttask.numtrials) ? "last" : "yes";
			if (new_percept.equals("yes")) {
				sttask.starttime = this.getElapsedTime() + this.secToMilli(1.2);
			}
			set_perception("pending", null, null);
			this.scheduleInput(1.2, Arrays.asList(new_percept, null, null));
		}
		
		return latency;
	}
	
	private void CM_setTask(String task) {
		taskMode = task;
		this.setTask(task, task); 	// TODO: CM uses random inputs. Will need to make fixed inputs for training sequence!
	}
	
	private void init_VCWM() {
		cstask.init();
		cstask.start = this.getElapsedTime() + this.secToMilli(0.365);
		this.scheduleInput(0.5, Arrays.asList("word", get_rand_word(), null));
	}
	private void init_stroop() {
		sttask.init();
		sttask.starttime = this.getElapsedTime() + 1000l;
		this.scheduleInput(1.0, Arrays.asList("yes", null, null));
	}
	
	private void do_stroop(int day, String condition) {
		this.setOutputFile("stroopCheinNR_s" + numSamples + ".txt");
		CM_setTask("stroop");
		for (int i=0; i<3 && !this.agentError; ++i) { //FIXME i<12
			init_stroop();
			
			this.runAgent();
			
			this.printReports(String.format("STROOP %s %d %d ", condition, i+1, day));
			this.clearReports();
		}
	}

	public void runCheinExperiment(String taskName, int samples, ArrayList<LearnConfig> expList) {
		numSamples = samples;
		this.runExperiments(taskName, samples, expList);
	}
	

	@Override
	protected void user_doExperiment() {
		// Run the control agent
		if (!this.initAgent()) return;
		do_stroop(1, "CONTROL");
		do_stroop(21, "CONTROL");

		// Run the transfer agent
		if (!this.initAgent()) return;
		do_stroop(1, "EXP");

		for (int i=0; i<2 && !this.agentError; ++i) {
			// Practice the verbal task
			System.out.println("*** Practice Session " + (i+1));
			CM_setTask("verbal-CWM");

			for (int j=0; j<16 && !this.agentError; ++j) {
				init_VCWM();

				this.runAgent();	// Run until receiving the finish command
			}
		}
		
		this.clearReports(); 	// We don't use the practice results
		this.setOutputFile("WMCheinNR_s" + numSamples + ".txt");
		
		for (int i=0; i<20 && !this.agentError; ++i) {
			// Test the verbal task
			System.out.println("*** Session " + (i+1));

			for (int j=0; j<16 && !this.agentError; ++j) {
				init_VCWM();

				this.runAgent();	// Run until receiving the finish command
			}
			this.printReports(String.format("%1d  ", i+1));
			this.clearReports();
		}
		
		if (this.agentError) {
			System.out.println("ERROR RETURNED BY AGENT " + "!");
			return;
		}
		
		
		do_stroop(21, "EXP");
		
		System.out.println("\nDone!");

	}

	@Override
	protected void user_outputListener(List<String> outputs) {

		// Get the output
		String action = outputs.get(0);
		String val2 = outputs.get(1);

		double latency = 0.0;

		if (taskMode.equals("stroop")) {
			latency = stroop_action(action, val2);
		}
		else if (taskMode.equals("verbal-CWM")) {
			latency = VCWM_action(action, val2);
		}
		else {
			// Unknown task - do nothing
		}
		
		this.addAgentLatency(secToMilli(latency));

		//rep.addLatency(latency);
		
	}


	@Override
	protected void user_agentStart() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void user_agentStop() {
		if (inDebug) {
			int currTaskInd = 0;
			//this.setTask(this.taskName, this.taskName + "_" + Integer.toString(currTaskInd + 1));
		}
		else {
			user_updateTask();
		}
	}

	@Override
	protected void user_createAgent() {
		// Called from initAgent() and runDebug(), when the agent is created
		if (inDebug) {
			if (taskMode.equals("verbal-CWM")) {
				this.scheduleInput(0.5, Arrays.asList("word", get_rand_word(), null));
			}
			else if (taskMode.equals("stroop")) {
				this.scheduleInput(1.0, Arrays.asList("yes", null, null));
			}
		}
	}

	@Override
	protected void user_updateTask() {

		// Start perception with "pending"
		this.setInput(0, "pending");
		this.applyNewInputs();
	}

	@Override
	protected void user_errorListener(String arg0) {
		System.err.println("ERROR DETECTED!");
	}

}
