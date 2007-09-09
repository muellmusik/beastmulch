// Matrixes for audio and and scaling with controllers

// ins and outs are either arrays of symbols corresponding to names
// in which case size equals the number of mappable inputs
// and index = busnum
// or InOutArrays of name -> busNum
// use the latter for arbitrary indices

// mappings is an Dictionary with in names as keys, and Lists of output names
// (Symbols) as values

// Issues:
// can input and output names be changed while this is running?

//InputArray and OutputArray classes should take care of indexes automatically. Keep the matrices stupid.

// Should InOutArrays for ins and outs offset channels by 1?

// CmdPeriod registration is automatic, unless turned off manually or by a MatrixManager

// At the moment AudioMatrices default to the head of the default group, AmpControlMatrices to the tail, but a Matrix Manager can control the ordering of this.

// Defines the minimum interface for a matrix AudioChainElement
BMAbstractMatrix : BMAbstractAudioChainElement {

	var <matrixArray, <mappings, defname; // defname is the def for a node
	
	*new { |ins, outs, group, server, name|
		^super.new.init(ins, outs, group, server ? Server.default, name ? this.name);
		// default name is class
	}
	
	init {|argins, argouts, arggroup, argserver, argname|
		ins = argins;
		outs = argouts;
		group = arggroup;
		server = argserver;
		name = argname;
		// allow for arrays as well as Dictionaries
		// if array, offset by input channels
		if(ins.isInOutArray.not, 
			{ins = ins.collectAs({|item, i| item -> (i + server.options.numInputBusChannels)}, 
				InOutArray);
		});
		if(outs.isInOutArray.not, 
			{outs = outs.collectAs({|item, i| item -> i}, InOutArray)});
		// used for indices for matrix lookup
		inNames = ins.keys;
		outNames = outs.keys;
		if(group.isNil, {this.makeGroup});
		this.newCollections;
		defname = "Node" ++ this.hash;
		this.sendDef;
		CmdPeriod.add(this);
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
		mappingsDict.keysValuesDo({|input, outputs| this.connect(input, outputs.asArray)});
	}
	
	cmdPeriod {
		this.newCollections;
		this.makeGroup;
		this.changed;
	}
	
	// this defeats CmdPeriod control
	// Allows for a MatrixManager to control ordering
	callCmdPeriod_ { |bool|
		bool.if({ CmdPeriod.add(this); }, {CmdPeriod.remove(this);});
		callCmdPeriod = bool;
	}
	
	// subclass stuff
	sendDef {
		^this.subclassResponsibility(thisMethod);
	}
	
	gui {
		^MatrixMenuGUI(this);
	}

}

// maps ins to outs
AudioMatrix : BMAbstractMatrix {
	
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
	
	makeGroup {
		group = Group.head(server);
	}
}

// maps amp scales to control busses
// roll your own curves etc. elsewhere
// this should in fact only allow an output to be connected to a single input 
AmpControlMatrix : BMAbstractMatrix {
	
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
	
	// a little hacky to call super here, but works
	connect { |input ... outputs|
		var currentIn;
		outputs.do({|output| 
			currentIn = outmappings[output];
			currentIn.notNil.if({this.disconnect(currentIn, output); });
			outmappings.add(output -> input);
		});
		super.connect(input, *outputs);
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
	
	makeGroup {
		group = Group.tail(server);
	}
}

// An Ordered Dictionary of associations (\name->index);
InOutArray : List {

	var <keys;
	
	*new {|size|
		^super.new(size).init;
	}
	
	*hardwareInputArray {|server|
		server = server.asTarget.server; // account for nil
		^HardwareInputsProxy.fill(server.options.numInputBusChannels, {|i| ("in" ++ (i+1)).asSymbol -> 			(server.options.numOutputBusChannels + i)});
	}
	
	init {
		keys = Array.new;
	}
	
	add { |assoc| var index;
		index = keys.indexOf(assoc.key);
		index.isNil.if({array = array.add(assoc); keys = keys.add(assoc.key);},
			{array.put(index, assoc)});
	}
	remove { ^this.shouldNotImplement(thisMethod) }
	removeAt {|key| var index;
		index = keys.indexOf(key);
		array.removeAt(key);
		keys.removeAt(index);
	}
	at {|key| var index;
		index = keys.indexOf(key);
		index.notNil.if({^array.at(index).value}, {^nil});
	}
	
	++ {|aCollection| ^this.addAll(aCollection)}
	
	isInOutArray {^true}
	
	asInOutArray {^this}

}

HardwareInputsProxy : InOutArray {

	name { ^"Hardware Inputs"; }
	
	gui { ^nil }
}

//temp
InputArray : InOutArray {

}

OutputArray : InOutArray {

}

MatrixMenuGUI : BMAbstractGUI {

	var matrix;
	var inputSection, assignSection, outputSection, inputView, assignView, outputView;
	var assignButton, labelPlusButton, matrixButton, clearButton, buttonSection;
	
	*new {|matrix, name, origin|
		^super.new.init(matrix, name ? matrix.name).makeWindow(origin ? (40@200));
	}
	
	init { |argmatrix, argname|
		matrix = argmatrix;
		name = argname;
		matrix.addDependant(this);
	}
	
	makeWindow { |origin|
		var x, y;
		x = origin.x;
		y = origin.y;
		
		window = SCWindow(name, Rect.new(x, y, 800, 300), false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		inputSection = SCVLayoutView(window, Rect(0, 0, 200, 300));
		SCStaticText.new(inputSection, Rect(0,0,180,20)).font_(Font("Crush49", 14))
			.string = "Inputs";
		inputView = SCListView(inputSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = false;
		inputView.items = matrix.inNames;
		inputView.action = {this.update};
		inputView.background = HiliteGradient(Color.blue.alpha_(0.3), 
			Color.green.alpha_(0.3), steps: 256);
		
		assignSection = SCVLayoutView(window, Rect(0, 0, 200, 300));
		labelPlusButton = SCHLayoutView(assignSection, Rect(0, 0, 200, 20));
		SCStaticText.new(labelPlusButton, Rect(0,0,180,20)).font_(Font("Crush49", 14))
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
		assignView.background = HiliteGradient(Color.blue.alpha_(0.5), 
			Color.green.alpha_(0.3), steps: 256);
		
		outputSection = SCVLayoutView(window, Rect(0, 0, 200, 300));
		labelPlusButton = SCHLayoutView(outputSection, Rect(0, 0, 200, 20));
		SCStaticText.new(labelPlusButton, Rect(0,0,80,20)).font_(Font("Crush49", 14))
			.string = "Outputs";
		assignButton = SCButton(labelPlusButton, Rect(0,0,110,20)).canReceiveDragHandler = false;		assignButton.states = [["<", Color.black,Color.clear]];
		assignButton.action = { matrix.connect(inputView.item, outputView.item);};
		outputView = SCListView(outputSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = false;
		outputView.beginDragAction = {|view| view.item };
		outputView.background = HiliteGradient(Color.blue.alpha_(0.7), 
			Color.green.alpha_(0.3), steps: 256);
		
		
		buttonSection = SCVLayoutView(window, Rect(0, 0, 150, 300));
		SCStaticText.new(buttonSection, Rect(0,0,80,20)).string_(" ");// placeholder
		
		clearButton = SCButton(buttonSection, Rect(0,0,110,20)).canReceiveDragHandler = false;		clearButton.states = [["Clear Matrix", Color.black,Color.clear]];
		clearButton.action = { matrix.clear};
		
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		
		matrixButton = SCButton(buttonSection, Rect(0,0,110,20)).canReceiveDragHandler = false;		matrixButton.states = [["View Matrix", Color.black,Color.clear]];
		matrixButton.action = { MatrixGUI(matrix, name)};
		
		SCStaticText.new(buttonSection, Rect(0,0,80,110)).string_("Assign outputs to selected input. Cmd-drag or use button to assign, select and press delete to unassign.");
		///.font_(Font("CoffeeCup", 40)).align_(\center);
		this.update;
		window.onClose = { matrix.removeDependant(this); onClose.value(this)};
		window.front;
	}
	
	update {
		assignView.items = matrix.mappings[inputView.item].asArray;
		outputView.items = matrix.outNames.difference(assignView.items);
	}
}

MatrixGUI : BMAbstractGUI {

	var h = 700, v = 700, numIns = 10, numOuts = 10, dotSize = 10;
	var hinterval, vinterval, tabletView;
	var cellsize = 70, screenBounds; // maximum cellsize
	var hoffset = 60, voffset = 20;
	var color, ringColor;
	var lastx, lasty, on = false;
	var ins, outs;
	var matrix;
	
	*new {|matrix, name|
		^super.new.init(matrix, name ? matrix.name).makeWindow;
	}
	
	init {|argmatrix, argname|
		matrix = argmatrix;
		name = argname;
		matrix.addDependant(this);
	}
	
	makeWindow {	
		ins = matrix.inNames;
		
		numIns = ins.size;
		
		outs = matrix.outNames;
		numOuts = outs.size;
		
		// scale size to available monitor size
		screenBounds = SCWindow.screenBounds;
		cellsize = cellsize min: (screenBounds.width - 40 - hoffset / numOuts); cellsize.postln;
		cellsize = cellsize min: (screenBounds.height - 40 - voffset / numIns); cellsize.postln;
		dotSize = cellsize * 0.33 min: 15; // maximum dot size
		if(cellsize < 35, {voffset = 35}); // double line of top labels
		h = numOuts * cellsize + hoffset;
		v = numIns * cellsize + voffset;
		
		
		 
		//color = Color.rand(0.0,1.0).alpha_(rrand(0.1,0.7)).set;
		color = Color.blue.alpha_(0.3).set;
		ringColor = color.copy.alpha_(1);
		
		window = SCWindow(name, Rect(40, 40, h, v), false);
		window.alpha = 0.9;
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
				
			});
			lastx = x; lasty = y;
		};
		
		window.front;
		window.drawHook = {
		
			Pen.width = 2;
			
			Pen.use {
				ringColor.set;
				// border lines
		
				Pen.line(hoffset@voffset, window.bounds.width@voffset);
				Pen.line(hoffset@voffset, hoffset@window.bounds.height);
		
				color.set;
				numIns.do { |i|
					Pen.line((1 + hoffset)@(vinterval + voffset + (i * vinterval)), 
						(window.bounds.width + hoffset)@(vinterval + voffset + 
						(i * vinterval)));
				};
				numOuts.do { |i|	 
					Pen.line((hinterval + hoffset + (i * hinterval))@(1 + voffset), (hinterval + 
						hoffset + (i * hinterval))@(window.bounds.height + voffset)); 
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

		};
		
		// write labels
		outs.do({|item, i|
			var y, t;
			if(voffset > 20, { y = [10, 25].wrapAt(i) }, { y = 10 });
			t = SCStaticText(window,
				Rect.aboutPoint((hoffset + hinterval + (hinterval * i))@y, 40, 10)
			);
			t.string = item.asString;
			t.stringColor = Color.black;
			t.font = Font("Andale Mono", 12);
			t.align = \center;
		});
		ins.do({|item, i| 
			var t;
			t = SCStaticText(window,
				Rect.aboutPoint((hoffset / 2)@(voffset + vinterval + (vinterval * i)), 40, 10)
			);
			t.string = item.asString.postln;
			t.stringColor = Color.black;
			t.font = Font("Andale Mono", 12);
			t.align = \center;
		});
		
		CmdPeriod.add(this);
		window.onClose = { 
			CmdPeriod.remove(this); matrix.removeDependant(this); onClose.value(this)
		};
		window.refresh;
	}
	
	cmdPeriod { window.refresh }
	
	update {window.refresh }
}