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
	var <>interval = 0.05; // update interval
	var lastCurrentTime;
	
	startUpdateLoop {
		running = true;
		Routine({
		while({running}, {
			this.automate;
			interval.wait;
		});
		}).play;
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
				
				// turn on the auto update loop if rate !=0, off if it does
				(rate != 0).if({ 
					running.not.if({this.startUpdateLoop;}); 
				}, {  
					running.if({this.stopUpdateLoop;});
					// check if we've moved while paused and update if so
					(time != lastTime).if({this.automate;});
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
Should snap be in *new?

Should automators be named?

How best to get representation from timeRef (i.e. sfview)

Should name come last in seq

*/
BMControllerAutomator : BMAbstractIndependentRateAutomator {
	// interpolates between controller snapshots
	var controls; // an array of controlnames or a single one
	var <sequences; // an dict of BMSnapShotSeqs
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
		sequences = IdentityDictionary.new;
		this.addToRef;
		oldSeqs = IdentitySet.new;
	}
	
	// if ctrlNames is nil add a global one
	addSequence {|seqName, startTime, ctrlNames|
		seqName = seqName.asSymbol;
		sequences.keys.includes(seqName).if({
			"Sequence Name" + seqName + "already in Use!".error; 
			^this;
		});
		sequences[seqName] = 
			BMSnapShotSeq(seqName, ctrlNames ? controls, startTime, sinSmooth.if({'sin'}, {'lin'}))
				.addDependant(this);
		this.changed(\sequencesChanged);
	}
	
	
	
	addStartSnapShot { }
	
	addSnapShot {|seqName, ssTime, ssName| 
		ssTime = ssTime ?? {BMTimeSources.currentTime(time, rate, referenceTime)};
		sequences[seqName].addSnapShot(ssTime, ssName);
	}
	
	addIndividualSequences {|seqName, startTime|
		
		controls.do({|ctrlname| 
			seqName = (seqName.asString ++ "-" ++ ctrlname).asSymbol;
			sequences.keys.includes(seqName).if({"Sequence Name already in Use!".error; ^this;
			},{
				sequences[seqName] =
					BMSnapShotSeq(seqName, startTime, sinSmooth.if({'sin'}, {'lin'}))
						.addDependant(this);
			});
		});
		this.changed(\sequencesChanged);
	}
	
	removeSequence { |seq|
		seq.isSymbol.if({seq = sequences[seq];});
		sequences[seq.name] = nil;
		seq.removeDependant(this);
		this.changed(\sequencesChanged);
	}
	
	sequence {|name| ^sequences[name.asSymbol] }
	
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
		var currentTime, values, control;
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
					control = BMAbstractController.allControls[ctrlname];
					// check another sequence hasn't touch this control
					if(control.lastAutomated != currentTime, {
						BMAbstractController.setValueByName(ctrlname, value);
						control.lastAutomated = currentTime;
					}, {
						("Multiple snapshot sequences simultaneously attempting to automate" 
							+ ctrlname).warn;
					});
				});
				
			}, {
				// check if seq should have ended and set end values
				// if we've leapt around don't worry about it
				
				seq.end.exclusivelyBetween(currentTime - interval, currentTime).if({
					//\sequenceEnd.postln;
//					("CT" + currentTime).postln;
//					("CT-" + (currentTime - interval)).postln;
					values = seq.atTime(seq.end);
					values.keysValuesDo({|ctrlname, value| 
						control = BMAbstractController.allControls[ctrlname];
						// check another sequence hasn't touch this control
						if(control.lastAutomated != currentTime, {
							BMAbstractController.setValueByName(ctrlname, value);
							control.lastAutomated = currentTime;
						}, {
							("Multiple snapshot sequences simultaneously attempting to automate" 
								+ ctrlname).warn;
						});
					});
				});
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
				this.changed(\sequencesChanged);
			},
			{super.update(changed, what, *args)}
		)
	}
}

// can't change controllers after starting!
BMSnapShotSeq {
	var <name, controls, <curve;
	var started = false;
	var <start, <end, <duration;
	//var lastAtTime = -inf; // in
	var arbStart, arbStartEnd;
	var arbEnd, arbEndEnd;
	var <snapshots, firstSnap, snapTimes, segs;
	var <snapshotsDict;
	var oldSeg;
	var <minSegSize = 0.2;
	
	*new {|name, controls, firstSnapTime, curve = 'lin'| // controls is an array of keys indicating control names
		^super.newCopyArgs(name, controls, curve).init(firstSnapTime);
	}
	
	init {|firstSnapTime|
		
		start = max(0, firstSnapTime - 1);
		
		firstSnap = BMArbitraryStartSnapShot(controls, start, 'Start');
		snapshots = [
			firstSnap,							// arbitrary
			BMSnapShot(controls, firstSnapTime, (name ++ "-1").asSymbol)	  	// known
		];
		snapshots.do(_.addDependant(this));
		snapshotsDict = IdentityDictionary.new; // by name rather than order
		snapshotsDict['Start'] = firstSnap;
		snapshotsDict[(name ++ "-1").asSymbol] = snapshots[1];
		this.buildSegs;
	}
	
	minSegSize_ {|newSize| minSegSize = newSize; this.buildSegs }
	
	addSnapShot {|time, ssname|
		var snap;
		ssname = ssname.asSymbol;
		snapshotsDict.keys.includes(ssname).if({
			"Snapshot" + ssname + "already exists.".error;
			^this;
		});
		if(time < start && firstSnap.isKnown.not, {
			start = max(0, time - 1);
			// avoid extra update
			firstSnap.removeDependant(this);
			firstSnap.time = start;
			firstSnap.addDependant(this);
		});
		
		snap = BMSnapShot(controls, time, ssname);
		snap.addDependant(this);
		snapshots = snapshots.add(snap).sort({|a, b| a.time < b.time });
		snapshotsDict[ssname] = snap;
		this.buildSegs;
	}
	
	removeSnapShot { |ssname|
		var snapshot;
		snapshot = snapshotsDict[ssname];
		snapshot.removeDependant(this);
		snapshots.remove(snapshot);
		snapshotsDict[ssname] = nil;
		this.buildSegs;
	}
	
	buildSegs {
		segs = [];
		// arb snapshot first, sort order correctly
//		snapshots = snapshots.sort({|a, b| a.time < b.time || (a === firstSnap)  });
//		if(firstSnap.time > snapshots[1].time, {
//			firstSnap.removeDependant(this);
//			firstSnap.time = max(snapshots[1].time - minSegSize, 0);
//			firstSnap.addDependant(this);
//		});
//		
		snapshots = snapshots.sort({|a, b| a.time < b.time });

		snapshots.collect(_.name).postln;
		snapshots.doAdjacentPairs({|a, b|
			// check minimum length
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
		snapTimes = snapshots.collect(_.time);
		this.changed(\segsBuilt);
	
	}
	
	atTime {|time| 
		var values, seg, ind;
		// return nil if not within this sequence's duration?
		if(this.containsTime(time), {
			// maybe better to cache this, and update when a snapshot is changed
			// using dependancy
			seg = segs[snapTimes.indexInBetween(time).trunc.clip(0, segs.size - 1)];
			//seg = segs.detect({|sg| time.inclusivelyBetween(sg.startSS.time, sg.endSS.time)});
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
		
		switch(what,
			\snap, {
				this.buildSegs;
				this.changed(\segsBuilt);
			},
			\snapTime, {
				this.buildSegs;
				this.changed(\segsBuilt);
			}
		)
	}

}

BMSnapShotSequenceSeg {
	var <startSS, <endSS, controls, curve;
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
	
	// Could also delay envs to save a subtraction
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
	// Could also delay envs to save a subtraction
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
	var <name, <time, <values;
	// these allow for customised behaviour upon entering a segment
	
	*new{|controls, time, name|
		^super.newCopyArgs(name).snap(controls, time);
	}
	
	time_ {|newTime| 
		time = newTime;
		this.changed(\snapTime.postln);
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
	
	*new{|controls, time, name|
		^super.newCopyArgs(name).init(time);
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
	var ca, envViews;
	var path, sf, sfView, scrollView, selectView, backView, menu;
	var activeSequence;
	var dependees;
	
	*new {|ca, name, origin|
		//^super.new.init(ca, name ? ca.name ? "test").makeWindow(origin ? (40@200));
		^super.new.init(ca, name ? "test").makeWindow(origin ? (40@200));
	}
	
	init {|argCa, argName|
		ca = argCa;
		name = argName;
		dependees = [ca.addDependant(this), ca.timeReference.addDependant(this)];
	}
	
	makeWindow {
		
		
		path = ca.timeReference.path; // How best to do this?
		sf = SoundFile.new;
		path.notNil.if({sf.openRead(path);});
		//f.openRead("sounds/a11wlk01.wav");
		//f.openRead("/Users/scottw/Music/SuperCollider\ Recordings/SC_080725_143355.aiff");
		
		window = SCWindow.new("Edit Snapshot Sequence", Rect(200, 200, 808, 400));
		window.view.decorator = FlowLayout(window.view.bounds);
		
		scrollView = SCScrollView(window, Rect(0, 0, 800, 334));
		scrollView.hasBorder = true;
		scrollView.resize = 2;
		//scrollView.background = Color.black;
		
		sfView = SCSoundFileView.new(scrollView, Rect(0,0, 798, 300));
		sfView.background = HiliteGradient(Color.blue, Color.cyan, steps: 256);
		//a.waveColors_([HiliteGradient(Color.blue, Color.cyan), HiliteGradient(Color.blue, Color.cyan)]);
		sfView.waveColors_(Array.fill(sf.numChannels, {|i| Color.blue.blend(Color.cyan, 1 / (sf.numChannels - 1) * i)})); 
		sfView.timeCursorOn = true;
		sfView.timeCursorColor = Color.red;
		
		scrollView.canFocus_(false);
		
		backView = SCCompositeView(scrollView, Rect(0,300,  798, 20)).background_(Color.black);
				
		sfView.soundfile = sf;
		
		sfView.elasticMode = 1;
		window.onClose = {sf.close; dependees.do({|dee| dee.removeDependant(this)});};
		
		
		SCStaticText(window, Rect(0, 0, 5, 10)).string_("-").font_(Font("Helvetica-Bold", 12));
		SmoothSlider(window, Rect(0, 0, 60, 10)).action_({|view| 
			var width;
			width = 798 + (sf.duration * 160 * view.value);
			sfView.bounds = Rect(0,0, width, 300);
			envViews.do({|ev| ev.bounds = Rect(0,300, width, 20); });
			backView.bounds = Rect(0,300, width, 20); 
			sfView.selections.size.do({|i| 
				sfView.setSelectionSize(i, sf.numFrames / sfView.bounds.width)
			});
			scrollView.refresh;
		}).knobSize_(1).canFocus_(false).hilightColor_(Color.blue);
		SCStaticText(window, Rect(0, 0, 10, 10)).string_("+").font_(Font("Helvetica-Bold", 10));
		
		window.view.decorator.nextLine.nextLine;
		SCStaticText(window, Rect(0, 0, 90, 15)).string_("Sequence to Edit").font_(Font("Helvetica-Bold", 10));
		menu = SCPopUpMenu(window, Rect(10,10,90,15))
			.font_(Font("Helvetica-Bold", 10))
			.action_({|view|
				envViews.do({|ev| ev.visible_(false)});
				envViews[view.value].visible_(true);
				activeSequence = ca.sequences[view.item];
				//this.clearSelections;
				this.drawSelections(envViews[view.value]);
				scrollView.refresh;
				//sfView.selections.postln;
			});
		
		sfView.readWithTask(block: 128, doneAction: {
			this.makeEnvViews;
		});
		//a.resize = 5;
		window.front;

	}
	
	makeEnvViews {
		envViews.do({|ev| ev.remove});
		envViews = [];
		ca.sequences.do({|seq, i|
			var envView;
			envView = SCEnvelopeView(scrollView, Rect(0, 300, sfView.bounds.width, 20))
				.thumbWidth_(90.0)
				.thumbHeight_(19)
				.drawLines_(true)
				.drawRects_(true)
				.selectionColor_(Color.grey)
				.strokeColor_(Color.white)
				.background_(Color.clear)
				.value_([
					seq.snapshots.collect({|ss| ss.time }) / sf.duration, // times
					0.1 ! seq.snapshots.size]) // values
				.visible_(false);
			//b.setStatic(0,true);
			seq.snapshots.do({arg ss, i;
				envView.setString(i, ss.name.asString + ss.time.asTimeString(0.01));
				//envView.setFillColor(i,Color.black);
			});
			
			
			envView.canFocus_(false);
		
			envView.mouseMoveAction = {|view|
				var time, ss;
				time = view.value[0][view.index];
				
				time.notNil.if({
					ss = seq.snapshots[view.index];
					sfView.setEditableSelectionStart(view.index, true);
					sfView.setEditableSelectionSize(view.index, true);
					sfView.setSelection(view.index, [sf.numFrames * time, sf.numFrames / sfView.bounds.width]); 
					sfView.setSelectionColor(view.index, Color.white);
					//ss.time = (time * sf.duration);
					envView.setString(view.index, ss.name.asString + (time * sf.duration).asTimeString(0.01));
					sfView.setEditableSelectionStart(view.index, false);
					sfView.setEditableSelectionSize(view.index, false);
					this.drawSelections(view);
				});
			};
			//envView.mouseUpAction = envView.mouseMoveAction;
			envView.mouseUpAction = {|view|
				var ss;
				view.index.notNil({
					ss = seq.snapshots[view.index];
					ss.time = view.value[0][view.index] * sf.duration;
					envView.value_([
						seq.snapshots.collect({|ss| ss.time }) / sf.duration, // times
						0.1 ! seq.snapshots.size]); // values
					seq.snapshots.do({arg ss, i;
						envView.setString(i, ss.name.asString + ss.time.asTimeString(0.01));
						//envView.setFillColor(i,Color.black);
					});
				});
			};
			envView.mouseDownAction = envView.mouseMoveAction;
			
			envViews = envViews.add(envView);
			
			menu.items_(ca.sequences.keys.asArray.sort).doAction;
		
		});

	}
	
	// we use SCSoundFileView Selections for the snapshot time cursors
	drawSelections {|view|
	
		var time, ss;
		
		
		this.clearSelections;
		activeSequence.snapshots.do({|ss, index|
			time = view.value[0][index];
			sfView.setEditableSelectionStart(index, true);
			sfView.setEditableSelectionSize(index, true);
			sfView.setSelection(index, [sf.numFrames * time, 
				sf.numFrames / sfView.bounds.width * 2]); 
			sfView.setSelectionColor(index, Color.white);
			//ss.time = (time * sf.duration);
			//view.setString(index, ss.name.asString + ss.time.asTimeString(0.01));
			sfView.setEditableSelectionStart(index, false);
			sfView.setEditableSelectionSize(index, false);
		});
		sfView.refresh;
	
	}
	
	clearSelections {
		64.do({|i| sfView.selectNone(i)});
	}
	
	update { arg changed, what ...args;
		switch(what,
			
			\time, {
				{sfView.timeCursorPosition = BMTimeSources.currentTime(args[0], args[1], args[2])
					* sf.sampleRate;}.defer;
			},
			\stop, {
				{sfView.timeCursorPosition = 0;}.defer;
			}//,
//			\segsBuilt, {
//				{this.makeEnvViews; this.drawSelections; }.defer;
//			}
		)
	
	}
}

//Quick and dirty for now
BMSnapShotSliders : BMAbstractGUI {
	var virtualCont, sliders, fromUpdate = false;
	var needsRefresh = false;
	var <>refreshInterval = 0.05;
	var refreshLoopOn = false;
	
	*new {|virtualCont, name, origin|
		^super.new.init(virtualCont, name ? virtualCont.name)
			.makeWindow(origin ? (40@200));
	}
	
	init {|argvirtualCont, argname|
		virtualCont = argvirtualCont;
		virtualCont.addDependant(this);
		name = argname;
	}
	
	makeWindow {|origin|
		var numSliders, presetMenu;
		numSliders = virtualCont.numFaders;
		window = SCWindow.new(name, 
			Rect(300, 300, 652, (numSliders + 1) * 24), false); // 508
		window.view.decorator = FlowLayout(window.view.bounds);
		window.view.background = Color.rand.alpha_(0.3);
		sliders = Array.newClear(numSliders);
		virtualCont.getAllLabels.do({|label, i|
			var initVal;
			initVal = virtualCont.getFaderVal(i + 1).ampdb;
			sliders[i] = EZSlider.new(window, 640@20, label.asString, \db,
				{|ez| var setVal;
					if(fromUpdate.not, {
						setVal = ez.value.dbamp;
						virtualCont.setFaderVal(i + 1, setVal);
					})
				}, initVal
			);
			sliders[i].numberView.boxColor = Color.white.alpha_(0.4);
		
		});
		window.onClose = { virtualCont.removeDependant(this); onClose.value };
		window.front;
	}
	
	// could be some jitter, but safer
	startRefreshLoop {
		refreshLoopOn.not.if({
			refreshLoopOn = true;
			AppClock.sched(refreshInterval, {
				var resched;
				needsRefresh.if({resched = refreshInterval}, {refreshLoopOn = false});
				fromUpdate = true; // prevent a loop
				virtualCont.getAllFaders.do({|val, i| 
					sliders[i].value_(val.ampdb);
				});
				fromUpdate = false;
				needsRefresh = false;
				resched;
			});
		});
	}
	
	update {|changed, what, index, val|
		switch(what,
			\faderVal, {
				needsRefresh = true;
				this.startRefreshLoop;
			},
			\label, {sliders[index].labelView.string_(val.asString)}
		)
	}
	

}