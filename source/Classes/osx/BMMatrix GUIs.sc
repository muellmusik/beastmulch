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
		matrix.takesControlsForInputs.if({
			matrix.inNames.do({|inName|
				BMAbstractController.allControls[inName].addDependant(this);
			});
		});
	}

	makeWindow { |origin|
		var x, y;
		x = origin.x;
		y = origin.y;

		window = Window(name, Rect.new(x, y, 800, 300), false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));

		inputSection = VLayoutView(window, Rect(0, 0, 200, 300));
		StaticText.new(inputSection, Rect(0,0,180,20)).font_(Font("Helvetica-Bold", 14))
			.string = "Inputs";
		inputView = ListView(inputSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = false;
		inputView.items = matrix.inNames;
		inputView.action = {this.update};
		inputView.background = Color.white.alpha_(0.2);

		assignSection = VLayoutView(window, Rect(0, 0, 200, 300));
		labelPlusButton = HLayoutView(assignSection, Rect(0, 0, 200, 20));
		StaticText.new(labelPlusButton, Rect(0,0,180,20)).font_(Font("Helvetica-Bold", 14))
			.string = "Assignments";
		assignView = ListView(assignSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = true;
		assignView.receiveDragHandler = {
			matrix.connect(inputView.item, View.currentDrag.asSymbol);
		};
		assignView.keyDownAction = { arg view,char,modifiers,unicode,keycode;
			if(unicode == 127, {
				matrix.disconnect(inputView.item, view.item);
			});
		};
		assignView.background = Color.white.alpha_(0.2);

		outputSection = VLayoutView(window, Rect(0, 0, 200, 300));
		labelPlusButton = HLayoutView(outputSection, Rect(0, 0, 200, 20));
		StaticText.new(labelPlusButton, Rect(0,0,80,20)).font_(Font("Helvetica-Bold", 14))
			.string = "Outputs";
		assignButton = RoundButton(labelPlusButton, Rect(0,0,110,20))
			.extrude_(false)
			.canFocus_(false)
			.canReceiveDragHandler = false;

		assignButton.states = [["<", Color.black, Color.white.alpha_(0.8)]];
		assignButton.action = { matrix.connect(inputView.item, outputView.item);};
		outputView = ListView(outputSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = false;
		outputView.beginDragAction = {|view| view.dragLabel = view.item.asString; view.item };
		outputView.background = Color.white.alpha_(0.2);


		buttonSection = VLayoutView(window, Rect(0, 0, 150, 300));
		StaticText.new(buttonSection, Rect(0,0,80,20)).string_(" ");// placeholder

		clearButton = RoundButton(buttonSection, Rect(0,0,110,20))
			.extrude_(false)
			.canFocus_(false)
			.canReceiveDragHandler = false;
		clearButton.states = [["Clear Matrix", Color.black, Color.white.alpha_(0.8)]];
		clearButton.action = { matrix.clear};

		StaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		StaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder

		matrixButton = RoundButton(buttonSection, Rect(0,0,110,20))
			.extrude_(false)
			.canFocus_(false)
			.canReceiveDragHandler = false;
		matrixButton.states = [["View Matrix", Color.black, Color.white.alpha_(0.8)]];
		matrixButton.action = { if (matrixGUI.isNil)
			   					{ matrixGUI = BMMatrixGUI(matrix, name);
			   		  			  matrixGUI.onClose_({ matrixGUI = nil })
			   					}
			   					{ matrixGUI.window.front }
			   			    };

		StaticText.new(buttonSection, Rect(0,0,80,110)).string_("Assign outputs to selected input. Cmd-drag or use button to assign, select and press delete to unassign.");
		this.update;
		window.onClose = {
			matrix.removeDependant(this);
			matrix.takesControlsForInputs.if({
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
		(matrix.takesControlsForInputs && BMOptions.allowMultipleControlMappings.not).if({
			mappedTo = BMAbstractController.allControls[inputView.item.asSymbol].mappedTo;
			if(mappedTo.notNil && (mappedTo !== matrix), {
				assignView.items = ["Mapped to" + mappedTo.name];
				assignView.enabled_(false);
			}, { assignView.enabled_(true).items = matrix.mappings[inputView.item].asArray; });
		}, { assignView.enabled_(true).items = matrix.mappings[inputView.item].asArray;});
		outputView.items = matrix.outNames.difference(assignView.items);
	}
}

BMMatrixGUI : BMAbstractGUI {

	var h = 700, v = 700, numIns = 10, numOuts = 10, dotSize = 10;
	var hinterval, vinterval, userView, userScrollView, insView, outsView, insScrollView, outsScrollView;
	var cellsize = 25, screenBounds; // maximum cellsize
	var hoffset = 80, voffset = 100, hRectHeight, vRectHeight;
	var color, ringColor;
	var lastx, lasty, on = false;
	var ins, outs;
	var matrix;
	var xpos = 1, ypos = 1; // draw lines initially
	var linex, liney, xdist, ydist;
	var font;
	var selecting = false, selectionStart, selectionEnd;
	var selections;
	var dragStart;

	*new {|matrix, name|
		^super.new.init(matrix, name ? matrix.name).makeWindow;
	}

	init {|argmatrix, argname|
		matrix = argmatrix;
		name = argname;
		matrix.addDependant(this);
		matrix.takesControlsForInputs.if({
			matrix.inNames.do({|inName|
				BMAbstractController.allControls[inName].addDependant(this);
			});
		});
		font = Font("Andale Mono", 11);
		selections = matrix.inNames.collectAs({|in| in->Set()}, IdentityDictionary);
	}

	makeWindow {

		var scrollFromInsOrOuts = false;

		ins = matrix.inNames;

		numIns = ins.size;

		outs = matrix.outNames;
		numOuts = outs.size;

		hoffset = max(hoffset, ins.collect({|lbl| lbl.asString.bounds(font).width}).maxItem + 20);
		voffset = max(voffset, outs.collect({|lbl| lbl.asString.bounds(font).width}).maxItem + 20);

		hRectHeight = ins.collect({|lbl| lbl.asString.bounds(font).height}).maxItem;
		vRectHeight = outs.collect({|lbl| lbl.asString.bounds(font).height}).maxItem;

		// scale size to available monitor size
		screenBounds = Window.screenBounds;
		dotSize = cellsize * 0.33;
		h = (numOuts * cellsize + hoffset) min: (screenBounds.width - 40);
		v = (numIns * cellsize + voffset) min: (screenBounds.height - 40);

		color = Color.blue.alpha_(0.5);
		ringColor = Color.black;

		window = Window(name, Rect(40, 40, h, v), false);

		userScrollView = ScrollView(
			window,
			Rect(hoffset, voffset, window.view.bounds.width - hoffset, window.view.bounds.height - voffset)
		);
		userView = UserView(userScrollView, Rect(0, 0, numOuts * cellsize, numIns * cellsize));
		hinterval = userView.bounds.width / (numOuts + 1);
		vinterval = userView.bounds.height / (numIns + 1);
		insScrollView = ScrollView(window, window.view.bounds.copy.top_(voffset).width_(hoffset));
		insScrollView = ScrollView(window, window.view.bounds.copy.top_(voffset).width_(hoffset));
		insScrollView.hasHorizontalScroller_(false).hasVerticalScroller_(false);
		outsScrollView = ScrollView(window, window.view.bounds.copy.left_(hoffset).height_(voffset));
		outsScrollView.hasHorizontalScroller_(false).hasVerticalScroller_(false);
		insView = UserView(insScrollView, Rect(0, 0, hoffset - 2, numIns * cellsize)); // -2 stops wiggles
		outsView = UserView(outsScrollView, Rect(0, 0, numOuts * cellsize, voffset - 2)); // -2 stops wiggles

		userScrollView.action = {
			var origin;
			if(scrollFromInsOrOuts.not, {
				origin = userScrollView.visibleOrigin;
				insScrollView.visibleOrigin = 0@(origin.y);
				outsScrollView.visibleOrigin = (origin.x)@0;
			});
		};

		insScrollView.action = {
			var origin;
			scrollFromInsOrOuts = true;
			origin = insScrollView.visibleOrigin;
			userScrollView.visibleOrigin = userScrollView.visibleOrigin.x@(origin.y);
			scrollFromInsOrOuts = false;
		};

		outsScrollView.action = {
			var origin;
			scrollFromInsOrOuts = true;
			origin = outsScrollView.visibleOrigin;
			userScrollView.visibleOrigin = (origin.x)@userScrollView.visibleOrigin.y;
			scrollFromInsOrOuts = false;
		};

		insView.mouseDownAction = { arg view,inx,iny, mods;
			dragStart = inx@iny;
		};

		insView.mouseMoveAction = { arg view,inx,iny, mods;
			var origin, diff;
			origin = insScrollView.visibleOrigin;
			diff = iny - dragStart.y;
			origin.y = (origin.y - diff) min: ((numIns * cellsize) - insScrollView.bounds.height);
			scrollFromInsOrOuts = true;
			insScrollView.visibleOrigin = origin;
			scrollFromInsOrOuts = false;
		};

		outsView.mouseDownAction = { arg view,inx,iny, mods;
			dragStart = inx@iny;
		};

		outsView.mouseMoveAction = { arg view,inx,iny, mods;
			var origin, diff;
			origin = outsScrollView.visibleOrigin;
			diff = inx - dragStart.x;
			origin.x = (origin.x - diff) min: ((numOuts * cellsize) - outsScrollView.bounds.width);
			scrollFromInsOrOuts = true;
			outsScrollView.visibleOrigin = origin;
			scrollFromInsOrOuts = false;
		};

		userView.mouseDownAction = { arg view,inx,iny, mods;
			if(mods.isAlt, {
				selecting = true;
				selectionStart = selectionEnd = inx@iny;
				window.refresh;
			},{
				var x, y;
				x = outs[(inx / hinterval).round.clip(1, numOuts) - 1];
				y = ins[(iny / vinterval).round.clip(1, numIns) - 1];
				if(matrix.mappings[y].indexOf(x).isNil, {matrix.connect(y, x); on = true;},
					{matrix.disconnect(y, x); on = false});
				lastx = x; lasty = y;
			});
		};
		userView.mouseUpAction = { arg view,inx,iny, mods;
			selecting = false;
			window.refresh;
		};
		// dragging
		userView.mouseMoveAction = { arg  view,inx,iny, mods;
			if(mods.isAlt, {
				selectionEnd = inx@iny;
				window.refresh;
			},{
				var x, y;
				x = outs[(inx / hinterval).round.clip(1, numOuts) - 1];
				y = ins[(iny / vinterval).round.clip(1, numIns) - 1];
				if((x != lastx) || (y != lasty), {
					linex = outs[(inx / hinterval).round.clip(1, numOuts) - 1];
					liney = ins[(iny / vinterval).round.clip(1, numIns) - 1];
					if(on, {
						if(matrix.mappings[y].indexOf(x).isNil, {
							matrix.connect(y, x);
					});},
					{
						if(matrix.mappings[y].indexOf(x).notNil, {
							matrix.disconnect(y, x);
						});
					});
					window.refresh;

				});
				lastx = x; lasty = y;
			});
		};

		// draw line for easy view
		userView.mouseOverAction = { arg view,inx,iny;
			xpos = (inx / hinterval);
			ypos = (iny / vinterval);
			linex = outs[xpos.round.clip(1, numOuts) - 1];
			liney = ins[ypos.round.clip(1, numIns) - 1];
			xdist = abs(xpos - xpos.round.clip(1, numOuts));
			ydist = abs(ypos - ypos.round.clip(1, numIns));
			window.refresh;
		};

		// don't draw white lines if we're not moused over
		userView.mouseLeaveAction = {
			linex = nil;
			liney = nil;
			userView.refresh;
		};

		// alt arrow to shift things
		userView.keyDownAction_({|view, char, mod, unicode, keycode, key|
			//[mod, unicode, keycode, key].postln;
			if(mod.isAlt && {[16777237, 16777235].includes(key)}, {
				var inNames, mappings, shift;
				inNames = matrix.inNames;
				mappings = IdentityDictionary();
				selections.keysValuesDo({|k, v| mappings[k] = v.as(List)});
				shift = if(key == 16777237, 1, -1);
				// clear old
				mappings.keysValuesDo({|k, v|
					matrix.disconnect(k, v);
				});
				selections = matrix.inNames.collectAs({|in| in->Set()}, IdentityDictionary);
				mappings.keysValuesDo({|k, v|
					var index, newKey;
					index = inNames.indexOf(k);
					newKey = inNames[index + shift];
					if(newKey.notNil, {
						matrix.connect(newKey, v);
						selections[newKey] = selections[newKey].addAll(v);
					})
				});
				window.refresh;
			});
			if(key == 16777216, { //esc
				selections = matrix.inNames.collectAs({|in| in->Set()}, IdentityDictionary);
				window.refresh;
			});
			true;
		});

		window.acceptsMouseOver = true;
		window.front;
		userView.drawFunc = {

			Pen.width = 2;

			Pen.use {
				// border lines

				Pen.line(0@0, window.bounds.width@0);
				Pen.line(0@0, 0@window.bounds.height);

				color.set;
				numIns.do { |i|
					if(ins[i] == liney && (ypos > 0), {
						Pen.stroke;
						Color.white.alpha_(1-ydist).set;
					});
					Pen.line(1@(vinterval + (i * vinterval)),
						(userView.bounds.width)@(vinterval +
						(i * vinterval)));
					if(ins[i] == liney, {Pen.stroke; color.set;});
				};
				numOuts.do { |i|
					if(outs[i] == linex && (xpos > 0), {
						Pen.stroke;
						Color.white.alpha_(1-xdist).set;
					});
					Pen.line((hinterval + (i * hinterval))@1, (hinterval +
						(i * hinterval))@(userView.bounds.height));
					if(outs[i] == linex, {Pen.stroke; color.set;});
				};
				Pen.stroke;
				matrix.matrixArray.do({ arg row, y;
					row.do({ arg item, x;
						var crosspoint, rect, selectRect;
						item.notNil.if({
							crosspoint = (hinterval + (x * hinterval))
								@(vinterval + (y * vinterval));
							rect = Rect.aboutPoint(crosspoint, dotSize, dotSize);
							if(selecting, {
								selectRect = Rect.fromPoints(selectionStart, selectionEnd);
								if(selectRect.containsPoint(crosspoint), {
									selections[ins[y]] = selections[ins[y]].as(Set).add(outs[x]);
								});
							});

							color.set;
							Pen.fillOval(rect);

							if(selections[ins[y]].includes(outs[x]), {
								Color.white.set;
							},{
								ringColor.set;
							});
							Pen.strokeOval(rect);
						});
					})
				});

			};
			// selectionRect
			if(selecting, {
				var selectRect;
				selectRect = Rect.fromPoints(selectionStart, selectionEnd);
				Color.white.alpha_(0.5).set;
				Pen.fillRect(selectRect);
				Color.white.alpha_(0.8).set;
				Pen.strokeRect(selectRect);
			});


		};

		insView.drawFunc = {

			Pen.width = 2;

			ins.do({|item, i|
				var inColor, mappedTo;

				(matrix.takesControlsForInputs && BMOptions.allowMultipleControlMappings.not).if({
					mappedTo = BMAbstractController.allControls[item].mappedTo;
					if(mappedTo.notNil && (mappedTo !== matrix), {
						inColor = Color.grey;
					}, { inColor = Color.black;});
				}, { inColor = Color.black;});


				Pen.use({
					Pen.translate((hoffset / 2), (vinterval + (vinterval * i)));
					item.asString.drawCenteredIn(Rect.aboutPoint(0@0, hoffset, 10),
						font,
						inColor
					);
				});
			});

		};

		outsView.drawFunc = {

			Pen.width = 2;

			outs.do({|item, i|

				Pen.use({
					Pen.translate((hinterval + (hinterval * i)), (voffset / 2));
					Pen.rotate(0.5pi);
					item.asString.drawCenteredIn(Rect.aboutPoint(0@0, voffset, 10),
						font,
						Color.black
					);
				});
			});

		};

		window.onClose = {
			matrix.removeDependant(this);
			matrix.takesControlsForInputs.if({
				matrix.inNames.do({|inName|
					BMAbstractController.allControls[inName].removeDependant(this);
				});
			});
			onClose.value(this)
		};
		window.refresh;
	}

	update { |changed, what| window.refresh; }
}
