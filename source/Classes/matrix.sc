// Matrixes for audio and and scaling with controllers

// ins and outs are either arrays of symbols corresponding to names
// in which case size equals the number of mappable inputs
// and index = busnum
// or BMInOutArrays of name -> busNum
// use the latter for arbitrary indices

// mappings is an Dictionary with in names as keys, and Lists of output names
// (Symbols) as values

// Issues:
// can input and output names be changed while this is running?

//InputArray and OutputArray classes should take care of indexes automatically. Keep the matrices stupid.

// Should BMInOutArrays for ins and outs offset channels by 1?

// CmdPeriod registration is automatic, unless turned off manually or by a MatrixManager

// At the moment AudioMatrices default to the head of the default group, AmpControlMatrices to the tail, but a Matrix Manager can control the ordering of this.

// Defines the minimum interface for a matrix AudioChainElement
BMAbstractMatrix : BMAbstractAudioChainElement {

	var <matrixArray, <mappings, defname; // defname is the def for a node
	
	*new { |ins, outs, group, server, name|
		^super.new.init(ins, outs, group, server ? Server.default, name);
		// default name is class
	}
	
	init {|argins, argouts, arggroup, argserver, argname|
		ins = argins;
		outs = argouts;
		group = arggroup;
		server = argserver;
		name = argname  ? this.makeName;
		// allow for arrays as well as Dictionaries
		// if array, offset by input channels
		if(ins.isBMInOutArray.not, 
			{ins = ins.collectAs({|item, i| item -> (i + server.options.numInputBusChannels)}, 
				BMInOutArray);
		});
		//ins.postln;
		if(outs.isBMInOutArray.not, 
			{outs = outs.collectAs({|item, i| item -> i}, BMInOutArray)});
		// used for indices for matrix lookup
		inNames = ins.keys;
		outNames = outs.keys;
		if(group.isNil, {this.makeGroup});
		this.newCollections;
		defname = ("BMMatrix-" ++ name);
//		postf("defname %\n", defname);
//		defname.do(_.postln);
		this.sendDef;
		CmdPeriod.add(this);
		allChainElements[name] = this;
	}
	
	newCollections {
		matrixArray = Array.newClear(outNames.size) ! ins.size;
		//should this be a set instead of a list?
		mappings = ins.keys.collectAs({|key| key -> List.new}, IdentityDictionary);
	}
	
	// allows for multiple outs mapped at once
	connect {  |input ... outputs| // Symbols
		// not so efficient, but okay for our purposes
		var inBus, outBus, inMatrixIndex, outMatrixIndex;
		(inBus = ins[input]).notNil.if({
			inMatrixIndex = inNames.indexOf(input);
			outputs = outputs.flat;
			outputs.do({ |out|
				(outBus = outs[out]).notNil.if({
					outMatrixIndex = outNames.indexOf(out);
					mappings[input].includes(out).not.if({
						matrixArray[inMatrixIndex][outMatrixIndex] = 
							Synth.new(defname, [\in, inBus, \out, outBus], group);
						mappings[input].add(out);
						this.changed;
					}, {warn(input ++ " already connected to " ++ out)});
				}, {error("Output:" + out + "is not defined.")});
			});
		}, {error("Input:" + input + "is not defined.")});
	}
	
	disconnect { |input ... outputs| // Symbols
		var inBus, outBus, inMatrixIndex, outMatrixIndex;
		(inBus = ins[input]).notNil.if({
			inMatrixIndex = inNames.indexOf(input);
			outputs = outputs.flat;
			outputs.do({|out|
				(outBus = outs[out]).notNil.if({
					outMatrixIndex = outNames.indexOf(out);
					matrixArray[inMatrixIndex][outMatrixIndex].release(BMOptions.crossfade);
					matrixArray[inMatrixIndex][outMatrixIndex] = nil;
					mappings[input].remove(out);
					this.changed;
				}, {error("Output:" + out + "is not defined.")});
			});
		}, {error("Input:" + input + "is not defined.")});
	}
	
	// currently will reset to different ins and outs. useful?
	clear { |time = 0.1|
		group.release(time);
		this.newCollections; // gc's all the Synths
		this.changed;
	}
	
	// then we're a lame duck
	free {
		this.clear;
		SystemClock.sched(0.1, {group.free; group = nil;});
		CmdPeriod.remove(this);
	}
	
	mappings_ {|mappingsDict| // same format as instance var
		this.clear;
		mappingsDict = mappingsDict ? ();
		mappingsDict.keysValuesDo({|input, outputs| this.connect(input, outputs.asArray)});
	}
	
	cmdPeriod {
		this.newCollections;
		this.makeGroup;
		this.changed;
	}
	
	// this defeats CmdPeriod control
	// Allows for a chain manager to control ordering
	callCmdPeriod_ { |bool|
		bool.if({ CmdPeriod.add(this); }, {CmdPeriod.remove(this);});
		callCmdPeriod = bool;
	}
	
	// subclass stuff
	sendDef {
		^this.subclassResponsibility(thisMethod);
	}
	
	gui {
		^BMMatrixMenuGUI(this);
	}
	
	controlsForInputs {^false }

}

// maps ins to outs
BMAudioMatrix : BMAbstractMatrix {
	
	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.new(inAudioArray, outAudioArray, group, server, name);
	}
	
	sendDef {
		SynthDef(defname, { arg in, out, gate = 1;
			// short fade in and out
			Out.ar(out, In.ar(in, 1) 
				* EnvGen.kr(Env.asr(BMOptions.crossfade, 1, BMOptions.crossfade), gate, doneAction: 2)
			);
		}).send(server);	
	}
	
//	makeGroup {
//		group = Group.head(server);
//	}
}

// maps amp scales to control busses
// roll your own curves etc. elsewhere
// this only allows an output to be connected to a single input 
BMAmpControlMatrix : BMAbstractMatrix {
	
	var outmappings;
	
	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.new(controllerArray, outAudioArray, group, server, name);
	}
	
	newCollections {
		matrixArray = Array.newClear(outNames.size) ! ins.size;
		//should this be a set instead of a list?
		mappings = ins.keys.collectAs({|key| key -> List.new}, IdentityDictionary);
		outmappings = IdentityDictionary.new;
	}
	
	connect { |input ... outputs|
		var currentIn, mappedTo;
		
		outputs = outputs.flat;
		
		if(outputs.size == 0, {^this }); // this edge case arises. why? preset updating?
		
		// outputs can inly be mapped to a single input (control)
		outputs.do({|output| 
			currentIn = outmappings[output];
			currentIn.notNil.if({this.disconnect(currentIn, output); });
		});
		
		// check if somebody else owns input
		BMOptions.allowMultipleControlMappings.not.if({ 
			mappedTo = BMAbstractController.allControls[input].mappedTo;
			if(mappedTo.notNil && (mappedTo !== this), {
				("Control mapping failed. Control" + input + "already controlling" + mappedTo.name).warn;
				^this;
			}, {
				BMAbstractController.allControls[input].mappedTo = this;
			});
		});
		
		outputs.do({|output| 
			outmappings.add(output -> input);
		});
		
		super.connect(input, *outputs);
	}
	
	disconnect { |input ... outputs| // Symbols
		var mappedTo;
		super.disconnect(input, *outputs);
		
		// if I'm not using this input (control) anymore release my claim
		BMOptions.allowMultipleControlMappings.not.if({ 
			mappedTo = BMAbstractController.allControls[input].mappedTo;
			if(mappings[input].size == 0 && (mappedTo === this), {
				BMAbstractController.allControls[input].mappedTo = nil;
			});
		});
	}
	
	clear {
		this.clearControlMappings;
		super.clear;
	}
	
	clearControlMappings {
		var control;
		BMOptions.allowMultipleControlMappings.not.if({ 
			inNames.do({|inName|
				control = BMAbstractController.allControls[inName];
				if(control.mappedTo === this, { 
					control.mappedTo = nil 
				});
			})
		});
	}
	
	cmdPeriod {
		this.clearControlMappings;
		super.cmdPeriod;
	}
	
	sendDef {
		SynthDef(defname, {arg in, out, gate = 1;
			// XFade in new scaled value, crossfade out when freeing so no clicks
			XOut.ar(out, 
				EnvGen.kr(Env.asr(BMOptions.crossfade, 1, BMOptions.crossfade), gate, doneAction: 2),
				In.ar(out, 1) * In.kr(in, 1)
			);
		}).send(server);
	}
	
//	makeGroup {
//		group = Group.tail(server);
//	}
	
	controlsForInputs { ^true }
}

// An Ordered Dictionary of associations (\name->index);
// Should this really be a subclass of list?
// We really need to protect against some methods
// Maybe better as a subclass of Dictionary

// reworked to build keys on demand.
// this is slow but much safer and simpler in terms of List compatibility
// do we need subArraysKeys
BMInOutArray : List {

	var subArraysKeys;
	var subArrays; // a dictionary of subArrayName->[key1, key2...]
	var busObjects;
	
	*new {|size|
		^super.new(size).init;
	}
	
	*hardwareInputArray {|server, name = "Hardware In"|
		server = server.asTarget.server; // account for nil
		^BMHardwareInputsProxy.fill(server.options.numInputBusChannels, {|i| 
			(name.asString + (i+1)).asSymbol -> (server.options.numOutputBusChannels + i)
		});
	}
	
	init {
		subArrays = IdentityDictionary.new;
		subArraysKeys = Array.new; // should this be a Set?
	}
	
	// get these when we need them
	keys {
		^this.collectAs({|item| item.key }, Array);
	}
	
	values { ^this.collectAs({|item| item.value }, Array); }
	
	*privateBusBlock {|name, size, server|
		^this.new(size).addPrivateBusBlock(name, size, server);
	}
	
	addPrivateBusBlock {|name, size, server|
		var bus, block;
		bus = Bus.audio(server, size);
		busObjects = busObjects.add(bus);
		block = BMInOutArray.fill(size, {|i| (name ++ " " ++ (i + 1)).asSymbol->(bus.index + i) });
		this.addAll(block);
		this.defineSubArray(name, block.keys);
	}
	
	// only do this if you're sure
	freeBusObjects { busObjects.do(_.free) }
	
	add { |assoc|
		var index;
		if(assoc.isValidBMInOutArrayMember.not, { 
			MethodError("Attempted to add invalid type to BMInOutArray", this).throw;
		}, {
			index = this.keys.indexOf(assoc.key);
			index.isNil.if({array = array.add(assoc);}, {array.put(index, assoc)});
		});
	}
	
	addFirst { |assoc|
		var index;
		if(assoc.isValidBMInOutArrayMember.not, { 
			MethodError("Attempted to add invalid type to BMInOutArray", this).throw;
		}, {
			index = this.keys.indexOf(assoc.key);
			index.isNil.if({
				array = array.addFirst(assoc);
			}, {
				MethodError("Item with key % already exists.".format(assoc.key), this);
			});
		});
	}
	
	insert { arg index, item; 
		if(item.isValidBMInOutArrayMember.not, { 
			MethodError("Attempted to add invalid type to BMInOutArray", this).throw;
		}, {
			this.keys.indexOf(item.key).isNil.if({
				array = array.insert(index, item); 
			}, {MethodError("Item with key % already exists.".format(item.key), this)});		});
	}

	removeAt {|key| 
		var index, val;
		index = this.keys.indexOf(key);
		index.notNil.if({
			subArrays.do({|sa| sa.remove(key)});
			val = array.removeAt(index);
		});
		^val.value
	}
	
	at {|keyOrIndex| var index;
		if(keyOrIndex.isNumber, { ^array.at(keyOrIndex).value; });
		index = this.keys.indexOf(keyOrIndex);
		index.notNil.if({^array.at(index).value}, {^nil});
	}
	
	//atIndex { |index| ^array.at(index) }
	
	// iffy?
	put { arg key, value;
		var atKey;
		var index;
		value ?? { this.removeAt(key); ^this };
		value = value.isBMSpeaker.if({value.name_(key)}, {key->value});
		this.add(key->value);
	}
	
	putAll { arg ... dictionaries; 
		dictionaries.do {|dict| 
			dict.keysValuesDo { arg key, value; 
				this.put(key, value) 
			}
		}
	}
	
	species {^this.class } // just in case
	
	++ {|aBMInOutArray| 
		var newlist = this.species.new(this.size + aBMInOutArray.size);
		newlist = newlist.addAll(this).addAll(aBMInOutArray);
		this.subArrays.do({|key| 
			newlist.defineSubArray(key, this.getSubArrayKeys(key));
		});
		aBMInOutArray.subArrays.do({|key| 
			newlist.defineSubArray(key, aBMInOutArray.getSubArrayKeys(key));
		});
		^newlist
	}

	defineSubArray {|name, elementNames| 
		var index;
		subArrays[name] = elementNames.sect(this.keys); 
		index = subArraysKeys.indexOf(name);
		if (index.isNil) { subArraysKeys = subArraysKeys.add(name) };	}
	
	getSubArray {|name| 
		^subArrays[name].collectAs({|key| this[key] }, this.class); 
	}
	
	getSubArrayKeys {|name | ^subArrays[name] }
	
	removeSubArray {|name|
		subArrays[name] = nil; 
		subArraysKeys.remove(name);
	}
	
	addToSubArray {| name, element |	
		subArrays[name] = subArrays[name].add(element); 
	}
	
	removeFromSubArray {| name, element |	
		subArrays[name].remove(element); 
	}
	
	subArrays {^subArraysKeys }
	
	isBMInOutArray {^true}
	
	asBMInOutArray {^this}
	
	asUGenInput { ^this.values.asUGenInput }
	
	asControlInput { ^this.values.asControlInput }

}

BMHardwareInputsProxy : BMInOutArray {

	name { ^"Hardware Inputs"; }
	
	gui { ^nil }
}

//temp
InputArray : BMInOutArray {

}

OutputArray : BMInOutArray {

}

BMMatrixMenuGUI : BMAbstractGUI {

	var matrix;
	var inputSection, assignSection, outputSection, inputView, assignView, outputView;
	var assignButton, labelPlusButton, matrixButton, clearButton, buttonSection;
	var <matrixGUI;
	
	*new {|matrix, name, origin|
		^super.new.init(matrix, name ? matrix.name).makeWindow(origin ? (40@200));
	}
	
	init { |argmatrix, argname|
		matrix = argmatrix;
		name = argname;
		matrix.addDependant(this);
		matrix.controlsForInputs.if({
			matrix.inNames.do({|inName|
				BMAbstractController.allControls[inName].addDependant(this);
			});
		});
	}
	
	makeWindow { |origin|
		var x, y;
		x = origin.x;
		y = origin.y;
		
		window = SCWindow(name, Rect.new(x, y, 800, 300), false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		inputSection = SCVLayoutView(window, Rect(0, 0, 200, 300));
		SCStaticText.new(inputSection, Rect(0,0,180,20)).font_(Font("Helvetica-Bold", 14))
			.string = "Inputs";
		inputView = SCListView(inputSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = false;
		inputView.items = matrix.inNames;
		inputView.action = {this.update};
		inputView.background = Color.white.alpha_(0.2);
		
		assignSection = SCVLayoutView(window, Rect(0, 0, 200, 300));
		labelPlusButton = SCHLayoutView(assignSection, Rect(0, 0, 200, 20));
		SCStaticText.new(labelPlusButton, Rect(0,0,180,20)).font_(Font("Helvetica-Bold", 14))
			.string = "Assignments";
//		SCStaticText.new(labelPlusButton, Rect(0,0,100,20)).font_(Font("Arial Bold", 10))
//			.string = "(delete to remove)";
		assignView = SCListView(assignSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = true;
		assignView.receiveDragHandler = { 
			matrix.connect(inputView.item, SCView.currentDrag.asSymbol);
		};
		assignView.keyDownAction = { arg view,char,modifiers,unicode,keycode;
			if(unicode == 127, {
				matrix.disconnect(inputView.item, view.item);
			});
		};
		assignView.background = Color.white.alpha_(0.2);
		
		outputSection = SCVLayoutView(window, Rect(0, 0, 200, 300));
		labelPlusButton = SCHLayoutView(outputSection, Rect(0, 0, 200, 20));
		SCStaticText.new(labelPlusButton, Rect(0,0,80,20)).font_(Font("Helvetica-Bold", 14))
			.string = "Outputs";
		assignButton = SCButton(labelPlusButton, Rect(0,0,110,20)).canReceiveDragHandler = false;		assignButton.states = [["<", Color.black,Color.clear]];
		assignButton.action = { matrix.connect(inputView.item, outputView.item);};
		outputView = SCListView(outputSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = false;
		outputView.beginDragAction = {|view| view.item };
		outputView.background = Color.white.alpha_(0.2);
		
		
		buttonSection = SCVLayoutView(window, Rect(0, 0, 150, 300));
		SCStaticText.new(buttonSection, Rect(0,0,80,20)).string_(" ");// placeholder
		
		clearButton = SCButton(buttonSection, Rect(0,0,110,20)).canReceiveDragHandler = false;		clearButton.states = [["Clear Matrix", Color.black,Color.clear]];
		clearButton.action = { matrix.clear};
		
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		
		matrixButton = SCButton(buttonSection, Rect(0,0,110,20)).canReceiveDragHandler = false;		matrixButton.states = [["View Matrix", Color.black,Color.clear]];
		matrixButton.action = { if (matrixGUI.isNil) 
			   					{ matrixGUI = BMMatrixGUI(matrix, name);
			   		  			  matrixGUI.onClose_({ matrixGUI = nil })
			   					}
			   					{ matrixGUI.window.front }
			   			    };
		
		SCStaticText.new(buttonSection, Rect(0,0,80,110)).string_("Assign outputs to selected input. Cmd-drag or use button to assign, select and press delete to unassign.");
		///.font_(Font("CoffeeCup", 40)).align_(\center);
		this.update;
		window.onClose = { 
			matrix.removeDependant(this); 
			matrix.controlsForInputs.if({
				matrix.inNames.do({|inName|
					BMAbstractController.allControls[inName].removeDependant(this);
				});
			});
			onClose.value(this)
		};
		window.front;
	}
	
	update {
		var mappedTo;
		(matrix.controlsForInputs && BMOptions.allowMultipleControlMappings.not).if({
			mappedTo = BMAbstractController.allControls[inputView.item.asSymbol].mappedTo;
			if(mappedTo.notNil && (mappedTo !== matrix), {
				assignView.items = ["Mapped to" + matrix.name];
				assignView.enabled_(false);
			}, { assignView.enabled_(true).items = matrix.mappings[inputView.item].asArray; });
		}, { assignView.enabled_(true).items = matrix.mappings[inputView.item].asArray;});
		outputView.items = matrix.outNames.difference(assignView.items);
	}
}

BMMatrixGUI : BMAbstractGUI {

	var h = 700, v = 700, numIns = 10, numOuts = 10, dotSize = 10;
	var hinterval, vinterval, tabletView;
	var cellsize = 40, screenBounds; // maximum cellsize
	var hoffset = 80, voffset = 100;
	var color, ringColor;
	var lastx, lasty, on = false;
	var ins, outs;
	var matrix;
	var xpos = 1, ypos = 1; // draw lines initially
	var linex, liney, xdist, ydist;
	var font;
	
	*new {|matrix, name|
		^super.new.init(matrix, name ? matrix.name).makeWindow;
	}
	
	init {|argmatrix, argname|
		matrix = argmatrix;
		name = argname;
		matrix.addDependant(this);
		matrix.controlsForInputs.if({
			matrix.inNames.do({|inName|
				BMAbstractController.allControls[inName].addDependant(this);
			});
		});
		font = Font("Andale Mono", 12);
	}
	
	makeWindow {	
		
		ins = matrix.inNames;
		
		numIns = ins.size;
		
		outs = matrix.outNames;
		numOuts = outs.size;
		
		hoffset = max(hoffset, ins.collect({|lbl| lbl.asString.bounds(font).width}).maxItem + 15);
		voffset = max(voffset, outs.collect({|lbl| lbl.asString.bounds(font).width}).maxItem + 15);
		
		// scale size to available monitor size
		screenBounds = SCWindow.screenBounds;
		cellsize = cellsize min: (screenBounds.width - 40 - hoffset / numOuts);
		cellsize = cellsize min: (screenBounds.height - 40 - voffset / numIns);
		dotSize = cellsize * 0.33 min: 15; // maximum dot size
		//if(cellsize < 35, {voffset = 35}); // double line of top labels
		h = numOuts * cellsize + hoffset;
		v = numIns * cellsize + voffset;
		
		
		 
		//color = Color.rand(0.0,1.0).alpha_(rrand(0.1,0.7)).set;
		color = Color.blue.alpha_(0.5).set;
		//ringColor = color.copy.alpha_(1);
		ringColor = Color.black;
		
		window = SCWindow(name, Rect(40, 40, h, v), false);
		window.alpha = 0.98;
		//window.view.background = Color.rand(0,0.3);
		window.view.background = Color.new255(140, 38, 255);
		hinterval = window.bounds.width - hoffset / (numOuts + 1);
		vinterval = window.bounds.height - voffset / (numIns + 1);
		tabletView = SCTabletView(window, window.view.bounds);
		tabletView.background = Color.clear;
		tabletView.mouseDownAction = { arg view,inx,iny;
			var x, y;
			x = outs[(inx - hoffset/ hinterval).round.clip(1, numOuts) - 1];
			y = ins[(iny - voffset/ vinterval).round.clip(1, numIns) - 1];
			if(matrix.mappings[y].indexOf(x).isNil, {matrix.connect(y, x); on = true;},
				{matrix.disconnect(y, x); on = false});
			lastx = x; lasty = y;
			//window.refresh;
		};
		// dragging
		tabletView.action = { arg  view,inx,iny;
			var x, y;
			x = outs[(inx - hoffset/ hinterval).round.clip(1, numOuts) - 1];
			y = ins[(iny - voffset/ vinterval).round.clip(1, numIns) - 1];
			if((x != lastx) || (y != lasty), {
				linex = outs[(inx - hoffset/ hinterval).round.clip(1, numOuts) - 1];
				liney = ins[(iny - voffset/ vinterval).round.clip(1, numIns) - 1];
				if(on, {
						if(matrix.mappings[y].indexOf(x).isNil, {
							matrix.connect(y, x);
							//window.refresh;
						});},
					{
						if(matrix.mappings[y].indexOf(x).notNil, {
							matrix.disconnect(y, x);
							//window.refresh;
						});
				});
				window.refresh;
				
			});
			lastx = x; lasty = y;
		};
		
		// draw line for easy view
		tabletView.mouseOverAction = { arg view,inx,iny;
			//\over.postln;
			xpos = (inx - hoffset/ hinterval);
			ypos = (iny - voffset/ vinterval);
			linex = outs[xpos.round.clip(1, numOuts) - 1];
			liney = ins[ypos.round.clip(1, numIns) - 1];
			xdist = abs(xpos - xpos.round.clip(1, numOuts));
			ydist = abs(ypos - ypos.round.clip(1, numIns));
			window.refresh;
		};
		
		window.acceptsMouseOver = true;
		window.front;
		window.drawHook = {
		
			Pen.width = 2;
			
			Pen.use {
				//ringColor.set;
				// border lines
		
				Pen.line(hoffset@voffset, window.bounds.width@voffset);
				Pen.line(hoffset@voffset, hoffset@window.bounds.height);
				//Pen.stroke;
				
				color.set;
				numIns.do { |i|
					if(ins[i] == liney && (ypos > 0), {
						Pen.stroke; 
						Color.white.alpha_(1-ydist).set;
					});
					Pen.line((1 + hoffset)@(vinterval + voffset + (i * vinterval)), 
						(window.bounds.width + hoffset)@(vinterval + voffset + 
						(i * vinterval)));
					if(ins[i] == liney, {Pen.stroke; color.set;});
				};
				numOuts.do { |i|	 
					if(outs[i] == linex && (xpos > 0), {
						Pen.stroke; 
						Color.white.alpha_(1-xdist).set;
					});
					Pen.line((hinterval + hoffset + (i * hinterval))@(1 + voffset), (hinterval + 
						hoffset + (i * hinterval))@(window.bounds.height + voffset)); 
					if(outs[i] == linex, {Pen.stroke; color.set;});
				};
				Pen.stroke;
				matrix.matrixArray.do({ arg row, y;
					row.do({ arg item, x;
						var crosspoint, rect;
						item.notNil.if({
							rect = Rect.aboutPoint((hinterval  + hoffset + (x * hinterval))
								@(vinterval + voffset + (y * vinterval)), dotSize, dotSize);
							color.set;
							Pen.fillOval(rect);
		
							ringColor.set;
							Pen.strokeOval(rect);
						});
					})
				});
				
			};
			outs.do({|item, i|
				
				Pen.use({
					Pen.translate((hoffset + hinterval + (hinterval * i)), (voffset / 2));
					Pen.rotate(0.5pi);
					item.asString.drawCenteredIn(Rect.aboutPoint(0@0, 40, 10), 
						font,
						Color.black
					);
				});
			});
			
			ins.do({|item, i|
				var inColor, mappedTo;

				(matrix.controlsForInputs && BMOptions.allowMultipleControlMappings.not).if({
					mappedTo = BMAbstractController.allControls[item].mappedTo;
					if(mappedTo.notNil && (mappedTo !== matrix), {
						inColor = Color.grey;
					}, { inColor = Color.black;});
				}, { inColor = Color.black;});

			
				Pen.use({
					Pen.translate((hoffset / 2), (voffset + vinterval + (vinterval * i)));
					//Pen.rotate(0.5pi);
					item.asString.drawCenteredIn(Rect.aboutPoint(0@0, 40, 10), 
						font,
						inColor
					);
				});
			});

		};
		
		// write labels
		//outs.do({|item, i|
//			var y, t;
//			//if(voffset > 20, { y = [10, 25].wrapAt(i) }, { y = 10 });
////			t = SCStaticText(window,
////				Rect.aboutPoint((hoffset + hinterval + (hinterval * i))@y, 40, 10)
////			);
////			t = SCStaticText(window,
////				Rect.aboutPoint((hoffset + hinterval + (hinterval * i))@(voffset / 2), 40, 10)
////			);
////			t.string = item.asString;
////			t.stringColor = Color.black;
////			t.font = Font("Andale Mono", 12);
////			t.align = \center;
//			
//			Pen.use({
//				Pen.translate((hoffset + hinterval + (hinterval * i)), (voffset / 2));
//				Pen.rotate(0.5pi);
//				item.asString.drawCenteredIn(Rect.aboutPoint(0@0, 40, 10).postln, 
//					Font("Andale Mono", 12),
//					Color.black
//				);
//			});
//		});

		// should change this to use Pen
		//ins.do({|item, i| 
//			var t, inColor;
//			t = SCStaticText(window,
//				Rect.aboutPoint((hoffset / 2)@(voffset + vinterval + (vinterval * i)), 40, 10)
//			);
//			t.string = item.asString;
//			(matrix.controlsForInputs && BMOptions.allowMultipleControlMappings.not).if({
//				var mappedTo;
//				mappedTo = BMAbstractController.allControls[item].mappedTo;
//				if(mappedTo.notNil && (mappedTo !== matrix), {
//					inColor = Color.grey;
//				}, { inColor = Color.black;});
//			}, { inColor = Color.black;});
//			t.stringColor = inColor;
//			t.font = Font("Andale Mono", 12);
//			t.align = \center;
//		});
		
		CmdPeriod.add(this);
		window.onClose = { 
			CmdPeriod.remove(this); 
			matrix.removeDependant(this); 
			matrix.controlsForInputs.if({
				matrix.inNames.do({|inName|
					BMAbstractController.allControls[inName].removeDependant(this);
				});
			});
			onClose.value(this)
		};
		window.refresh;
	}
	
	cmdPeriod { window.refresh }
	
	update { |changed, what| window.refresh; }
}
