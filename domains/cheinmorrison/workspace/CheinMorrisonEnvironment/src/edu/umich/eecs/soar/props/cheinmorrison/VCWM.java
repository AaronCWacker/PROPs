package edu.umich.eecs.soar.props.cheinmorrison;

import java.util.ArrayList;
import java.util.List;

public class VCWM {
	public String state;
	public Boolean last_correct;
	public List<ArrayList<String>> spans;
	public ArrayList<String> responses,
							stimuli;
	public int current_span,
				trials,
				count;
	public long start;
	
	VCWM() {
		init();
	}
	
	public void init() {
		state = "lexical";
		current_span = 4;
		last_correct = null;
		trials = 16;
		spans = new ArrayList<ArrayList<String>>();
		responses = new ArrayList<String>();
		stimuli = new ArrayList<String>();
		count = current_span - 1;
		start = 365l;
		//schedule_delayed_action(0.5, "word", get_rand_word());
	}

	@Override
	public String toString() {
		return String.format("VCWM: Current-Span %d, Trials %d, State %s, Start %.3f, Count %d, Stimuli %s", current_span, trials, state.toUpperCase(), start/0.001, count, stimuli.toString());
	}
}
