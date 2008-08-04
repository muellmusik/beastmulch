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
}

// rate of this and time ref are independent
BMAbstractIndependentRateAutomator : BMAbstractAutomator {
	var <>interval; // nil interval means update with time ref
	
	startUpdateLoop {
		running = true;
		Routine({
		while({running}, {
			loop({
				this.automate;
				interval.wait;
			});
		});
		}).play;
	}
	
	// this is the simple case, but you can override to have
	// more complicated cleanup
	stopUpdateLoop {
		running = false;
	}
	
	update {arg changed, what ...args; 
		//if(what == \n_end, {stopwatch.stop;});
		switch(what,
			\n_end, {
			
			},
//			\play, {stopwatch.start;},
			\playFailed, {

				},
			\stop, {
				time = 0; // needed?
				this.reset;
				},
			\time, { time = args[0]; rate = args[1]; referenceTime = args[1]; 
				
				// check if we've moved while paused and update if so
				(rate == 0  || interval.isNil && (time != lastTime)).if({
					this.automate;
				});
				
				// turn on the auto update loop if rate !=0, off if it does
				(rate != 0).if({ 
					this.startUpdateLoop; 
				}, {  
					this.stopUpdateLoop;
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

----

How should an individual control ensure that it isn't double mapped or double automated.
One way is to have a control in a plugin etc. be contollable either by a control or an automater, but not both. That still leaves the case of multiple control automators though. 

Do we need to have multiple sequences going automatically.

----
Currently overlapping automator with different controls are impossible.
Maybe just go through all the sequences, but flag each control within an automate cycle to make sure it isn't double automated.

*/
BMControllerAutomator : BMAbstractIndependentRateAutomator {
	// interpolates between controller snapshots
	var controllers; // an array of controllers, or one controller
	var sequences; // an array of arrays of BMSnapShotSeq
	var oldSeq;
	
	*new { |controllers, timeref|
		^super.new.init(controllers, timeref);
	}
	
	init {|argctrllrs, argref|
		controllers = argctrllrs.asArray;
		timeReference = argref;
		this.addToRef;
	}
	
	addSequence {}
	
	// how to deal with bundling?
	automate {
		var currentTime, values;
		currentTime = BMTimeSources.currentTime(time, rate, referenceTime);
		currentSeq = sequences.detect({|seq| // there can be only one
			seq.containsTime(currentTime);
		});
		if(oldSeq != currentSeq, {currentSeq.reset});
		values = currentSeq.atTime(currentTime);
		values.keysValuesDo(|ctrlname, value| 
			BMAbstractController.setValueByName(ctrlname, value);
		});
	}
	
	reset { sequences.do(_.reset); oldSeq = nil;}
}

// can't change controllers after starting!
BMSnapShotSeq {
	var controls, snapshots;
	var started = false;
	var start, duration;
	//var lastAtTime = -inf; // in
	var arbStart, arbStartEnd;
	var arbEnd, arbEndEnd;
	var segs;
	var oldSeg;
	
	*new {|controls| // controllers is an array of keys indicating control names
		^super.newCopyArgs(controls);
	}
	
	atTime {|time| 
		var values;
		// return nil if not within this sequence's duration?
		if(this.containsTime, {
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
	
	containsTime {|time| ^time.inclusivelyBetween(start, start + duration);}
	
	reset { 
		//segs.do(_.makeInactive); 
		oldSeg = nil; 
	}
}

BMSnapShotSequenceSeg {
	var startSS, endSS, controls, curve = 'sin';
	var envs, known;
	//var activated = false;
	
	*new {|startSS, endSS, controls, curve|
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
		
		});
	}
	
}

BMAbstractSnapShot {
	var <>time, <values;
	// these allow for customised behaviour upon entering a segment
	
	snap {|controls, argTime|  
		time = argTime;
		values = controls.collectAs({|ctrlname| 
			ctrlname -> BMAbstractController.getValueByName(ctrlname);
		}, IdentityDictionary);
	}
	makeActive { this.subclassResponsibility(thisMethod); }
	makeInActive { this.subclassResponsibility(thisMethod); }
	
	isKnown {^true}
}

// a known state
BMSnapShot : BMAbstractSnapShot {
	// no-ops
	makeActive { } 
	makeInActive { }
}

// for unknown start (and maybe end) states
BMArbitraryStartSnapShot : BMAbstractSnapShot {
	var activated = false;
	makeActive {|controls, argTime|  
		this.tempsnap(controls, argTime); // **** need my own snap and
	}
	
	tempsnap {}
	
	makeInActive { values = nil; activated = false;}
	
	isKnown {^false}
}


BMControllerAutomatorGUI : BMAbstractGUI {

	makeWindow {
	
	}
}