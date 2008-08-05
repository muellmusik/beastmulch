BMAbstractAutomator {
	var time, rate, referenceTime;
	var lastTime; // last received from time ref
	var <timeReference; // my clock source
	var <running = false;
	update {  this.subclassResponsibility(thisMethod);}
	
	addToRef {timeReference.addDependant(this);}
	
	removeFromRef {timeReference.removeDependant(this);}
	
	timeReference_ {|ref|
		this.removeFromRef;
		timeReference = ref;
		this.addToRef;
	}
	
	// what this is depends on subclass
	automate { this.subclassResponsibility(thisMethod);} 
	
	mappings { this.subclassResponsibility(thisMethod);}
	
	mappings_ { this.subclassResponsibility(thisMethod);} 
}

// rate of this and time ref are independent
BMAbstractIndependentRateAutomator : BMAbstractAutomator {
	var <>interval; // nil interval means update with time ref
	
	startUpdateLoop {
		interval.notNil.if({
			running = true;
			Routine({
			while({running}, {
				loop({
					this.automate;
					interval.wait;
				});
			});
			}).play;
		});
	}
	
	// this is the simple case, but you can override to have
	// more complicated cleanup
	stopUpdateLoop {
		running = false;
	}
	
	reset { }
	
	update {arg changed, what ...args; 
		//if(what == \n_end, {stopwatch.stop;});
		switch(what,
			\n_end, {
			
			},
			//\play, { this.startUpdateLoop; },
			\playFailed, {

				},
			\stop, {
				time = 0; // needed?
				this.stopUpdateLoop;
				this.reset;
				},
			\time, { time = args[0]; rate = args[1]; referenceTime = args[2]; 
				
//				time.postln;
//				("ref:" + referenceTime).postln;
//				("main:" + Main.elapsedTime).postln;
				// check if we've moved while paused and update if so
				(rate == 0  || interval.isNil && (time != lastTime)).if({
					this.automate;
				});
				
				// turn on the auto update loop if rate !=0, off if it does
				(rate != 0).if({ 
					running.not.if({this.startUpdateLoop;}); 
				}, {  
					running.if({this.stopUpdateLoop;});
				});
				
				lastTime = time;
			}
		)
	}
}


/*
The logic for this is actually quite complicated. We need to allow for:

- unspecified start (and possibly end states)
- variable rates of playback, both positive and negative
- starting in the middle of a sequence

Solution 1:
when you enter a segment with an arbitrary state as one of its points just assign the current fader values to that point regardless of positive or negative rate. When you leave that segment clear it.

Solution 2:
As above, but add an extra interpolation point making a flat line segment between the start and current state.

NB Making a new env is very low cost.

Solution 2 is current

----

At the moment there can be only one automator assigned to each control.
It is possible to have multiple controller automators (for instance assigned to different time references) providing they don't try to automate the same controls.
A single automator can have overlapping sequences, but if they try to update the same controller in the same automation cycle all but the first will fail. 

For more elaborate and fine tuned control, use the DAW like automator object, under a single fader. Or we could have a more elaborate ControllerAutomator which has envelopes for the entire duration.
	- this would be easy to do. Just have separate sequences for each fader.
----
Should this be a singleton?
	- no, you might have automators separate time references automating controls

----

To do:

Add initial fader state
Decide if we need a separate class for the DAW version
Should snap be in *new

*/
BMControllerAutomator : BMAbstractIndependentRateAutomator {
	// interpolates between controller snapshots
	var controls; // an array of controlnames or a single one
	var <sequences; // an array of BMSnapShotSeqs
	var oldSeqs;
	var sinSmooth = true;
	
	*new { |controls, timeref|
		^super.new.init(controls, timeref);
	}
	
	init {|argctrls, argref|
		controls = argctrls.asArray;
		controls.copy.do({|ctrlname| 
			var ctrl;
			ctrl = BMAbstractController.allControls[ctrlname];
			ctrl.automator.isNil.if({
				ctrl.automator = this;
			}, {
				("Controller automation assignment failed. Control" + ctrlname 
					+ "already has an automator").warn;
				controls.remove(ctrlname);
			});
		});
		timeReference = argref;
		this.addToRef;
		oldSeqs = IdentitySet.new;
	}
	
	addGlobalSequence {|startTime|
		sequences = sequences.add(
			BMSnapShotSeq(controls, startTime, sinSmooth.if({'sin'}, {'lin'})).addDependant(this)
		);
		this.changed(\sequencesChanged);
	}
	
	addStartSnapshot { }
	
	addIndividualSequences {|startTime|
		controls.do({|ctrlname| 
			sequences = sequences.add(
				BMSnapShotSeq(ctrlname, startTime, sinSmooth.if({'sin'}, {'lin'}))
					.addDependant(this)
			);
		});
		this.changed(\sequencesChanged);
	}
	
	removeSequence { |seq|
		seq.removeDependant(this);
		this.changed(\sequencesChanged);
	}
	
//	// how to deal with bundling?
//	automate {
//		var currentTime, values;
//		currentTime = BMTimeSources.currentTime(time, rate, referenceTime);
//		currentSeq = sequences.detect({|seq| // there can be only one
//			seq.containsTime(currentTime);
//		});
//		if(oldSeq != currentSeq, {currentSeq.reset});
//		values = currentSeq.atTime(currentTime);
//		values.keysValuesDo(|ctrlname, value| 
//			BMAbstractController.setValueByName(ctrlname, value);
//		});
//	}

	// how to deal with bundling?
	automate {
		var currentTime, values;
		currentTime = BMTimeSources.currentTime(time, rate, referenceTime);
		//currentTime.postln;
		sequences.do({|seq|
			seq.containsTime(currentTime).if({
				oldSeqs.findMatch(seq).isNil.if({
					seq.reset;
					oldSeqs.add(seq);
				});
				values = seq.atTime(currentTime);
				values.keysValuesDo({|ctrlname, value| 
					// check another sequence hasn't touch this control
					if(BMAbstractController.allControls[ctrlname].lastAutomated != currentTime, {
						BMAbstractController.setValueByName(ctrlname, value);
						BMAbstractController.allControls[ctrlname].lastAutomated = currentTime;
					}, {
						("Multiple snapshot sequences simultaneously attempting to automate" 
							+ ctrlname).warn;
					});
				});
				
			}, {
				oldSeqs.remove(seq);
			});
		});
	}
	
	reset { sequences.do(_.reset); oldSeqs.clear;}
	
	free { controls.do({|ctrl| ctrl.automator = nil});}
	
	update {arg changed, what ...args; 
		//if(what == \n_end, {stopwatch.stop;});
		switch(what,
			\segsBuilt, {
				this.changed(\sequenceChanged);
			},
			{super.update(changed, what, *args)}
		)
	}
}

// can't change controllers after starting!
BMSnapShotSeq {
	var controls, <curve, snapshots;
	var started = false;
	var start, end, duration;
	//var lastAtTime = -inf; // in
	var arbStart, arbStartEnd;
	var arbEnd, arbEndEnd;
	var <snapshots, firstSnap, segs;
	var oldSeg;
	var <minSegSize = 0.2;
	
	*new {|controls, firstSnapTime, curve = 'lin'| // controllers is an array of keys indicating control names
		^super.newCopyArgs(controls, curve).init(firstSnapTime);
	}
	
	init {|firstSnapTime|
		
		start = max(0, firstSnapTime - 1);
		
		firstSnap = BMArbitraryStartSnapShot(controls, start);
		snapshots = [
			firstSnap,							// arbitrary
			BMSnapShot(controls, firstSnapTime)	  	// known
		];
		snapshots.do(_.addDependant(this));
		this.buildSegs;
	}
	
	minSegSize_ {|newSize| minSegSize = newSize; this.buildSegs }
	
	addSnapShot {|time|
		var snap;
		if(time < start, {
			start = max(0, time - 1);
			// avoid extra update
			firstSnap.removeDependant(this);
			firstSnap.time = start;
			firstSnap.addDependant(this);
		});
		
		snap = BMSnapShot(controls, time);
		snapshots.add(snap);
		snapshots.sort({|a, b| a.time < b.time });
		this.buildSegs;
	}
	
	removeSnapShot { |snapshot|
		snapshot.removeDependant(this);
		snapshots.remove(snapshot);
		this.buildSegs;
	}
	
	buildSegs {
		segs = [];
		snapshots.doAdjacentPairs({|a, b|
			// check minimum length
			a.dump;
			b.dump;
			if(b.time - a.time < minSegSize, {
				b.removeDependant(this);
				b.time = a.time + minSegSize;
				b.addDependant(this);
			});
			segs = segs.add(BMSnapShotSequenceSeg(a, b, controls, curve));
		});
		start = snapshots.first.time;
		end = snapshots.last.time;
		duration = end - start;
		this.changed(\segsBuilt);
	
	}
	
	atTime {|time| 
		var values;
		// return nil if not within this sequence's duration?
		if(this.containsTime(time), {
			var seg;
			// maybe better to cache this, and update when a snapshot is changed
			// using dependancy
			seg = segs[snapshots.collect(_.time).indexInBetween(time).trunc.clip(0, segs.size)];
			if(seg != oldSeg, {
				//oldSeg.notNil.if({oldSeg.makeInactive}); // clean house
				seg.makeActive(time);
				oldSeg = seg;
			});
			values = controls.collectAs({|ctrlname| 
				ctrlname -> seg.atTime(ctrlname, time);
			}, IdentityDictionary);
			
		}); 
		^values // exclusive values return nil
	}
	
	containsTime {|time| ^time.inclusivelyBetween(start, end);}
	
	reset { 
		//segs.do(_.makeInactive); 
		oldSeg = nil; 
	}
	
	update {arg changed, what ...args; 
		//if(what == \n_end, {stopwatch.stop;});
		switch(what,
			\snap, {
				this.buildSegs;
			},
			\snapTime, {
				this.buildSegs;
			}
		)
	}

}

BMSnapShotSequenceSeg {
	var startSS, endSS, controls, curve;
	var envs, known;
	//var activated = false;
	
	*new {|startSS, endSS, controls, curve = 'sin'|
		^super.newCopyArgs(startSS, endSS, controls, curve).init;
	}
	
	init {
		known = startSS.isKnown && endSS.isKnown;
		//known.if({this.makeEnvs});
	}
	
	makeActive { |time|
		//activated.not.if({
			startSS.makeActive(controls, time);
			endSS.makeActive(controls, time);
			//activated = true;
			//known.not.if({this.makeEnvs;});
			this.makeEnvs;
		//});
	}
	
	atTime {|ctrlname, time|
		^envs[ctrlname][time - startSS.time]
	}
	
//	makeInActive {
//		startSS.makeInActive;
//		endSS.makeInActive; 
//		activated = false;
//	}
	// might optimise here to check if both ss are known and cache envs if true
	// maybe not worth it
	makeEnvs {
		known.if({
			envs = controls.collectAs({|ctrlname| 
				ctrlname -> Env(
					[startSS.values[ctrlname], endSS.values[ctrlname]], 
					[endSS.time - startSS.time], 
					curve
				);
			}, IdentityDictionary);
		}, {
			// with a flat segment at start
			envs = controls.collectAs({|ctrlname| 
				ctrlname -> Env(
					[startSS.values[ctrlname], startSS.values[ctrlname], endSS.values[ctrlname]], 
					[startSS.snapTime - startSS.time, endSS.time - startSS.snapTime], 
					curve
				);
			}, IdentityDictionary);
			
		});
	}
	
}

BMAbstractSnapShot {
	var <time, <values;
	// these allow for customised behaviour upon entering a segment
	
	*new{|controls, time|
		^super.new.snap(controls, time);
	}
	
	time_ {|newTime| 
		time = newTime;
		this.changed(\snapTime);
	}
	
	snap {|controls, argTime|  
		time = argTime;
		values = controls.collectAs({|ctrlname| 
			ctrlname -> BMAbstractController.getValueByName(ctrlname);
		}, IdentityDictionary);
		this.changed(\snap);
	}
	makeActive { this.subclassResponsibility(thisMethod); }
	//makeInActive { this.subclassResponsibility(thisMethod); }
	
	isKnown {^true}
}

// a known state
BMSnapShot : BMAbstractSnapShot {
	// no-ops
	makeActive { } 
	//makeInActive { }
}

// for unknown start (and maybe end) states
BMArbitraryStartSnapShot : BMAbstractSnapShot {
	//var activated = false;
	var <snapTime;
	
	*new{|controls, time|
		^super.new.init(time);
	}
	
	init {|argTime| time = argTime; }
	
	makeActive {|controls, argTime|  
		this.tempsnap(controls, argTime); 
	}
	
	snap { } // don't pass go, no $200
	
	tempsnap {|controls, argTime|  
		snapTime = argTime;
		values = controls.collectAs({|ctrlname| 
			ctrlname -> BMAbstractController.getValueByName(ctrlname);
		}, IdentityDictionary);
		//this.changed(\snap);
	}
	
	//makeInActive { values = nil; activated = false;}
	
	isKnown {^false}
}


BMControllerAutomatorGUI : BMAbstractGUI {

	makeWindow {
	
	}
}