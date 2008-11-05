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
	
	timeInitialised { ^(time.notNil && rate.notNil && referenceTime.notNil) }
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
	var <controls; // an array of controlnames or a single one
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
		ssTime = ssTime ?? {
			if(this.timeInitialised, {
				BMTimeSources.currentTime(time, rate, referenceTime);
			}, {0});
		};
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
	var <minSegSize = 0.15;
	
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
		snapshots = snapshots.sort({|a, b| a.time < b.time || (a === firstSnap)  });
		postf("snapshots(buildSegs): %\n", snapshots);
		if(firstSnap.time > snapshots[1].time, {
			\first.postln;
			firstSnap.removeDependant(this);
			firstSnap.time = max(snapshots[1].time - minSegSize, 0);
			firstSnap.addDependant(this);
		});
		
		snapshots = snapshots.sort({|a, b| a.time < b.time });
		
		postf("snapshots(buildSegs): %\n", snapshots.collect(_.name));
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
		this.changed(\snapTime);
	}
	
	snap {|controls, argTime|  
		time = argTime;
		values = controls.collectAs({|ctrlname| 
			ctrlname -> BMAbstractController.getValueByName(ctrlname);
		}, IdentityDictionary);
		postf("snap values: %\n", values);
		this.changed(\snap);
	}
	makeActive { this.subclassResponsibility(thisMethod); }
	//makeInActive { this.subclassResponsibility(thisMethod); }
	
	isKnown {^true}
	
	setValue {|ctrl, value| values[ctrl] = value; this.changed(\snap);}
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


// When you select a seq it should become active
// You can select a snapshot by clicking it
// Have an option to hide the non-active seq

// delete deletes a snapshot (not indeterminate ones)
// add ss adds to active seq at insertion point
// prompt if insertion point or active sequence off screen

// maybe move clock out of points

// snapshot opens a window with current controller states and toggles for inclusion

// probably this should be made into a subclass with alternate makeWindows for timerefs
// which are not soundfiles

BMControllerAutomatorGUI : BMAbstractGUI {
	var ca, <envView;
	var path, sf, sfView, scrollView, selectView, backView, menu;
	var activeSequence, activeSnapshot;
	var dependees;
	var seqs, snapshots, names, times, connections;
	var showOnlySelected = false;
	var curSSTime;
	
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
		
		window.view.decorator.shift(0, 5);
		SCStaticText(window, Rect(0, 0, 5, 10)).string_("-").font_(Font("Helvetica-Bold", 12));
		SmoothSlider(window, Rect(0, 5, 100, 10)).action_({|view| 
			var width;
			width = 798 + (sf.duration * 160 * ([0.001, 1.001, \exp].asSpec.map(view.value) - 0.001));
			sfView.bounds = Rect(0,0, width, 300);
			envView.bounds = Rect(0,300, width, 20);
			backView.bounds = Rect(0,300, width, 20); 
			sfView.selections.size.do({|i| 
				sfView.setSelectionSize(i, sf.numFrames / sfView.bounds.width);
			});
			this.drawSelections;
//			envView.refresh;
			this.resetPoints;
			scrollView.refresh;
		}).knobSize_(1).canFocus_(false).hilightColor_(Color.blue);
		SCStaticText(window, Rect(0, 0, 10, 10)).string_("+").font_(Font("Helvetica-Bold", 10));
		window.view.decorator.shift(0, -5);
		
		//SCStaticText(window, Rect(0, 0, 90, 15)).string_("Sequence to Edit").font_(Font("Helvetica-Bold", 10));
//		menu = SCPopUpMenu(window, Rect(10,10,90,15))
//			.font_(Font("Helvetica-Bold", 10))
//			.action_({|view|
//				envViews.do({|ev| ev.visible_(false)});
//				envViews[view.value].visible_(true);
//				activeSequence = ca.sequences[view.item];
//				//this.clearSelections;
//				this.drawSelections(envViews[view.value]);
//				scrollView.refresh;
//				//sfView.selections.postln;
//			});
		
		sfView.readWithTask(block: 256, doneAction: {
			this.makeEnvView;
		});
		
//		RoundButton(window, 80@20).extrude_(false)
//			.canFocus_(false)
//			.font_(Font("Helvetica-Bold", 10))
//			.states_([["Add Sequence"]])
//			.action_({ca.addSequence(UniqueID.next.asSymbol, 0)}); // global sequence

		RoundButton(window, 120@20).extrude_(false)
			.canFocus_(false)
			.font_(Font("Helvetica-Bold", 10))
			.states_([["Show Only Selected"], ["Show Only Selected", Color.black, Color.grey]])
			.action_({|view|
				showOnlySelected = view.value.booleanValue;
				this.makeEnvView;
			});		
		RoundButton(window, 120@20)
			.extrude_(false)
			.canFocus_(false)
			.font_(Font("Helvetica-Bold", 10))
			.states_([["Add Snapshot"]])
			.action_({
				ca.addSnapShot(activeSequence.name, nil, UniqueID.next.asSymbol);
				this.makeEnvView;
				menu.doAction;
			});
		RoundButton(window, 120@20)
			.extrude_(false)
			.canFocus_(false)
			.font_(Font("Helvetica-Bold", 10))
			.states_([["Add Sequence"]])
			.action_({
				//ca.addSequence(activeSequence.name, nil, UniqueID.next.asSymbol);
				this.makeEnvView;
				menu.doAction;
			});
		window.view.decorator.nextLine.nextLine;
		curSSTime = SCStaticText(window, Rect(0, 0, 300, 20))
			.string_("Current Snapshot Time:") // initialise
			.font_(Font("Helvetica-Bold", 12));
		//a.resize = 5;
		window.front;

	}
	
	makeEnvView {
		envView.notNil.if({envView.remove});
		
		envView = SCEnvelopeView(scrollView, Rect(0, 300, sfView.bounds.width, 20))
			.thumbWidth_(19)
			.thumbHeight_(19)
			.drawLines_(true)
			.drawRects_(true)
			.selectionColor_(Color.grey)
			.strokeColor_(Color.white)
			.background_(Color.clear);
			
		//b.setStatic(0,true);
		
		
		if(activeSequence.isNil, {activeSequence = ca.sequences.values[0]});
		if(activeSnapshot.isNil, {activeSnapshot = activeSequence.snapshots[0]});
		
		this.resetPoints;
		this.setFillColors;
		
		this.drawSelections;
		envView.canFocus_(false);
	
		envView.mouseMoveAction = {|view|
			var time, ss;
			time = view.value[0][view.index];
			
			time.notNil.if({
				curSSTime.string_("Current Snapshot Time:" + (sf.duration * time).asTimeString); 
				ss = snapshots[view.index];
				sfView.setEditableSelectionStart(view.index, true);
				sfView.setEditableSelectionSize(view.index, true);
				sfView.setSelection(view.index, [sf.numFrames * time, sf.numFrames / sfView.bounds.width]); 
				sfView.setSelectionColor(view.index, Color.white);
				//ss.time = (time * sf.duration);
				//envView.setString(view.index, ss.name.asString + (time * sf.duration).asTimeString(0.01));
				sfView.setEditableSelectionStart(view.index, false);
				sfView.setEditableSelectionSize(view.index, false);
				this.drawSelections;
			});
		};
		//envView.mouseUpAction = envView.mouseMoveAction;
		envView.mouseUpAction = {|view|
			var ss, seq, index, next, prev, selected;
			index = view.index;
			postf("index: %\n", index);
			(index >= 0).if({
				postf("ss(mouseUp): %\n", snapshots);
				selected = snapshots[index];
				snapshots[index].time = view.value[0][index] * sf.duration;
				
				\foo.postln;
				
//				// correct for crossovers
//				next = snapshots[index + 1];
//				if(next.notNil && {snapshots[index].time > next.time}, {
//					envView.selectIndex(index + 1);
//					envView.refresh;
//				});
//				prev = snapshots[index - 1];
//				if(prev.notNil && {snapshots[index].time < prev.time}, {
//					envView.selectIndex(index - 1);
//					envView.refresh;
//				});

				this.resetPoints;
				this.drawSelections;
				
				this.setFillColors;
				//this.makeEnvView;
				//\mousUpNotNil.postln;
//				ss = snapshots[index];
//				seq = seqs[index];
//				ss.time = view.value[0][index] * sf.duration;
//				envView.value_([
//					seq.snapshots.collect({|ss| ss.time }) / sf.duration, // times
//					0.1 ! seq.snapshots.size]); // values
//				// a little inefficient, but works
//				snapshots.do({arg snsh, i;
//					envView.setString(i, snsh.name.asString + snsh.time.asTimeString(0.01));
//					//envView.setFillColor(i,Color.black);
//				});
			});
		};
		envView.mouseDownAction = {|view|
			// deselects on click in midst
			activeSequence = seqs[view.index];
			activeSnapshot = snapshots[view.index];
			this.setFillColors;
			this.drawConnections;
			envView.mouseMoveAction.value(envView);	
		};
		
		//menu.items_(ca.sequences.keys.asArray.sort).doAction;
	

	}
	
//	selectSnapShot {|selected|
//		var index;
//		index = snapshots.indexOf(selected);
//		envView.selectIndex(index);
//	
//	}
	
	setFillColors {
		var color;
		snapshots.do({|ss, i|
			color =  if(ss === activeSnapshot, {Color.grey}, {
				if(seqs[i] === activeSequence, {Color.blue}, {Color.black})
			});
			envView.setFillColor(i, color);
		});
	}
	
	resetPoints {
		seqs = List.new;
		snapshots = List.new;
		names = List.new;
		times = Array.new;
		showOnlySelected.not.if({
			ca.sequences.do({|seq|
				seq.snapshots.do({|ss|
					var time;
					time = ss.time;
					times = times.add(time / sf.duration);
					names.add(ss.name.asString + ss.time.asTimeString(0.01));
					seqs.add(seq); // for ordered lookup
					snapshots.add(ss);
				});
			});
		}, {
		
			activeSequence.snapshots.do({|ss|
				var time;
				time = ss.time;
				times = times.add(time / sf.duration);
				names.add(ss.name.asString + ss.time.asTimeString(0.01));
				seqs.add(activeSequence); // for ordered lookup
				snapshots.add(ss);
			});
		});
		
		// values
		envView.value_([times, 0.1 ! times.size]); 
		
		// connections
		//seqs.doAdjacentPairs({|a,b, i| if(a===b, {envView.connect(i, [i +1])})});
//		seqs.doAdjacentPairs({|a,b, i| if(a === b && (a === activeSequence), {
//			i.postln;
//			envView.connect(i, [i +1])}, {envView.connect(i, [])})});
		this.drawConnections;
		
		//// labels
//		names.do({arg name, i;
//			envView.setString(i, name);
//			//envView.setFillColor(i,Color.black);
//		});
		snapshots.do({arg ss, i;
			envView.setString(i, ss.isKnown.if({""}, {"?"}));
		});
	}
	
	drawConnections {
		seqs.doAdjacentPairs({|a,b, i| if(a === b && (a === activeSequence), {
			envView.connect(i, [i +1])
		}, {envView.connect(i, [])})});
	}
	
	// we use SCSoundFileView Selections for the snapshot time cursors
	drawSelections {
	
		var time;
		//view.value.postln;
		
		this.clearSelections;
		snapshots.do({|ss, index|
			if(seqs[index] === activeSequence, {
				time = envView.value[0][index];
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
//			\sequencesChanged, {
//				{this.makeEnvViews; this.drawSelections(envViews[menu.value]);}.defer;
//			}
		)
	
	}
}

//Quick and dirty for now
BMSnapShotSliders : BMAbstractGUI {
	var snapshot, sliders, fromUpdate = false;
	var needsRefresh = false;
	var <>refreshInterval = 0.05;
	var refreshLoopOn = false;
	
	*new {|snapshot, origin|
		^super.new.init(snapshot)
			.makeWindow(origin ? (40@200));
	}
	
	init {|argss|
		snapshot = argss;
		snapshot.addDependant(this);
	}
	
	makeWindow {|origin|
		var numSliders, presetMenu;
		numSliders = snapshot.values.size;
		window = SCWindow.new(snapshot.name, 
			Rect(300, 300, 652, (numSliders + 1) * 24), false); // 508
		window.view.decorator = FlowLayout(window.view.bounds);
		window.view.background = Color.rand.alpha_(0.3);
		sliders = IdentityDictionary.new;
		snapshot.values.keys.sort.do({|label, i|
			var initVal;
			initVal = snapshot.values[label];
			sliders[label] = EZSlider.new(window, 640@20, label.asString, nil,
				{|ez| 
					if(fromUpdate.not, {
						
						snapshot.setValue(label, ez.value);
					})
				}, initVal
			);
			sliders[label].numberView.boxColor = Color.white.alpha_(0.4);
		
		});
		window.onClose = { snapshot.removeDependant(this); onClose.value };
		window.front;
	}
	
	// could be some jitter, but safer
//	startRefreshLoop {
//		refreshLoopOn.not.if({
//			refreshLoopOn = true;
//			AppClock.sched(refreshInterval, {
//				var resched;
//				needsRefresh.if({resched = refreshInterval}, {refreshLoopOn = false});
//				fromUpdate = true; // prevent a loop
//				snapshot.getAllFaders.do({|val, i| 
//					sliders[i].value_(val.ampdb);
//				});
//				fromUpdate = false;
//				needsRefresh = false;
//				resched;
//			});
//		});
//	}
	
	update {|changed, what, index, val|
		switch(what,
			\snap, {
				//needsRefresh = true;
//				this.startRefreshLoop;
				{
				fromUpdate = true; // prevent a loop
				snapshot.values.keysValuesDo({|key, value| 
					sliders[key].value = value; });
				fromUpdate = false;
				}.defer;
				
			}
		)
	}
	

}