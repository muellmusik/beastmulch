BMAbstractAutomator {
	var time, rate, referenceTime;
	var <timeReference; // my clock source
	var <>running = false;
	update {  this.subclassResponsibility(thisMethod);}
	
	addToRef {timeReference.addDependant(this);}
	
	removeFromRef {timeReference.removeDependant(this);}
	
	timeReference_ {|ref|
		this.removeFromRef;
		timeReference = ref;
		this.addToRef;
	}
	
	atTime { this.subclassResponsibility(thisMethod);} // what this is depends on subclass
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

*/
BMControllerAutomator : BMAbstractAutomator {
	// interpolates between controller snapshots
	var controllers; // an array of controllers, or one controller
	var sequences; // an array of arrays of BMSnapShotSeq
	
	*new { |controllers, timeref|
		^super.new.init(controllers, timeref);
	}
	
	init {|argctrllrs, argref|
		controllers = argctrllrs.asArray;
		timeReference = argref;
		this.addToRef;
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
				},
			\time, { time = args[0]; rate = args[1]; referenceTime = args[1]; 
				//running.if({this.updateControllers});
			}
		)
	}
	
	atTime {|timeref|
	
	}
	
	addSequence {}
	
	updateControllers {
		var currentTime, values;
		currentTime = BMTimeSources.currentTime(time, rate, referenceTime);
		values = this.atTime(currentTime);
		values.notNil.if({
			controllers.do({|ctrllr|
				values[ctrllr.name].do({|vals|
					ctrllr.setFaders(vals);
				});
			});
		});
	
	}
}

// can't change controllers after starting!
BMSnapShotSeq {
	var controllers, snapshots;
	var started = false;
	var start, duration;
	//var lastAtTime = -inf; // in
	var arbStart, arbStartEnd;
	var arbEnd, arbEndEnd;
	var oldSeg;
	
	*new {|controllers| // controllers is an array of keys indicating control names
		^super.newCopyArgs(controllers);
	}
	
	atTime {|time| 
		// return nil if not within this sequence's duration?
		^if(this.containsTime, {
			var seg;
			// maybe better to cache this, and update when a snapshot is changed
			// using dependancy
			seg = snapshots.collect(_.time).indexInBetween(time).trunc;
			if(seg != oldSeg, {
				snapshots[[oldSeg, oldSeg + 1]].do({|ss| ss.makeInActive(controllers) });
				snapshots[[seg, seg + 1]].do({|ss| ss.makeActive(controllers) });
			});
			
		}, nil); // exclusive values return nil
	}
	
	containsTime {|time| ^time.inclusivelyBetween(start, start + duration);}
}

// a known state
BMAbstractSnapShot {
	var <>time, values;
	// these allow for customised behaviour upon entering a segment
	
	snap {|controllers|  
		values = controllers.collectAs({|ctrlName| BMAbstractController.getValueByName(ctrlName)});
	}
	makeActive { this.subclassResponsibility(thisMethod); }
	makeInActive { this.subclassResponsibility(thisMethod); }
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
	makeActive {|controllers|  
		this.snap(controllers);
		activated = true;
	}
	
	makeInActive { values = nil; activated = false;}
}


BMControllerAutomatorGUI : BMAbstractGUI {

	makeWindow {
	
	}
}