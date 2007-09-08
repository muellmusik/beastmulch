// Controls order of multiple audio chain elements
// Takes an Array of elements and sets their groups in corresponding order within the target group
// starting at the tail. Bundling ensures ordering.

// Note that AbstractMatrix-cmdPeriod calls this.changed, so any dependencies will be updated before the bundle is sent. This may not be desirable, and possibly should be factored out

// elements is an array containing elements of arrays of elements
// the latter is used by the GUI



BMAudioChainManager {
	var <sources, <sourceProcessing, <outputProcessing, <controllerArray, <outputArray;
	var <privateBusArray, <group;
	var <sourceArray, <elements, <audioMatrix;
	
	// pre and post go before and after an audiomatrix which does routing
	// sources and outputarray are instances
	// processing arrays contain (name->class) associations and are automatically constructed
	
	*new {|sources, sourceProcessing, outputProcessing, controllerArray, outputArray, 
		privateBusArray, group|
		^super.newCopyArgs(sources, sourceProcessing, outputProcessing, controllerArray, 
			outputArray, privateBusArray, group.asGroup).init; 
		// default target is default Server
	}
	
	// could check ServerOptions here to make sure they're correct
	init {
		this.initChain;
		elements = sources.reject(_.isInOutArray) 
			++ [sourceProcessing, audioMatrix, outputProcessing].flat;
		CmdPeriod.add(this);
		group.server.makeBundle(nil, {
			elements.do({|element| 
				element.callCmdPeriod_(false); 
				element.group.moveToTail(group);
			});
		});
	}
	
	// auto construct the chain

	initChain {
		sources.do({|source| sourceArray = sourceArray ++ source.asInOutArray});
		sourceArray = sourceArray ++ privateBusArray;
		sourceProcessing = sourceProcessing.collect({|item|
			item.value.newFromChain(controllerArray, sourceArray, sourceArray, nil, group.server, 
				item.key);
		});
		audioMatrix = AudioMatrix(sourceArray, outputArray, nil, group.server, 'Audio Routing');
		outputProcessing = outputProcessing.collect({|item|
			item.value.newFromChain(controllerArray, outputArray, outputArray, nil, group.server, 
				item.key);
		});
	}
	
	cmdPeriod {
		group.server.makeBundle(nil, {
			elements.do({|element| element.cmdPeriod; element.group.moveToTail(group)});
		});
	}
	
	remove { |reactivateCP = false|
		CmdPeriod.remove(this);
		reactivateCP.if({ elements.do({|element| element.callCmdPeriod_(true);}) })
	}

}

// display an element order and generates and tracks element GUIs
// if an item in chain is an array it goes at the same level
BMAudioChainManagerGUI : BMAbstractGUI {
	var <manager, name, guis, objects;
	var chainView;
	
	*new {|manager, name, origin|
		^super.new.init(manager, name ? "Signal Chain").makeWindow(origin ? (40@200));
	}
	
	init {|argManager, argName|
		manager = argManager;
		name = argName;
		guis = IdentityDictionary.new; // use Objects as keys
	}

	makeWindow {|origin|
		var x, y, rows, columns, width, pseudoLevels, pseudoTimes, count = 0, selected;
		var points, rects, selectedIndex;
		x = origin.x;
		y = origin.y;
		objects = manager.sources 
			++ [manager.sourceProcessing, manager.audioMatrix, manager.outputProcessing].flat;
		selected = false ! objects.size;
		rows = objects.size - manager.sources.size + 1;
		columns = manager.sources.size;
		width = max(450, columns * 150);
		
		pseudoLevels = (1..rows).normalize * 0.8 + 0.1;
		pseudoLevels = pseudoLevels.collect({|item, i| if(i == 0, {item ! columns}, {item})}).flat;
		pseudoTimes = [(1..columns).normalize - 0.5 * 0.68 + 0.5, 0.5 ! (rows - 1)].flat;
		
		window = SCWindow(name, Rect.new(x, y, width, 450), false, scroll: true);
		if(width == 450, {window.view.hasHorizontalScroller = false; });
		chainView = SCUserView(window, Rect(0, 0, width, max(450, rows * 80)));
		window.view.background = Color.white.alpha_(0.2);
		pseudoLevels = pseudoLevels * chainView.bounds.height;
		pseudoTimes = pseudoTimes * chainView.bounds.width;
		
		points = Array.fill(objects.size, {|i|  Point(pseudoTimes[i], pseudoLevels[i])});
		rects = points.collect({|point| Rect.aboutPoint(point, 60, 25)});

		chainView.drawFunc_({
			// draw lines
			columns.do({|i| Pen.line(points[i], points[columns])});
			(rows - 2).do({|i| Pen.line(points[i + columns], points[i + columns + 1])});
			Pen.stroke;
			
			// draw backgrounds, boxes and strings
			rects.do({|rect, i|
				selected[i].if({Color.grey.alpha_(0.5)}, {Color.grey}).set;
				Pen.fillRect(rect);
				Color.black.set;
				Pen.strokeRect(rect);
				objects[i].name.asString.drawCenteredIn(rect, Font("Arial", 12), Color.black);
			});
		});		
		chainView.mouseDownAction = {|view, x, y|
			var hitpoint, element;
			hitpoint = x@y;
			selectedIndex = rects.detectIndex({|rect| rect.containsPoint(hitpoint)});
			if(selectedIndex.notNil, { 
				selected[selectedIndex] = true; 
				element = objects[selectedIndex];
				guis[element].notNil.if({ 
					guis[element].window.front;
				},{

					guis[element] = element.gui;
					guis[element].notNil.if({guis[element].onClose_({guis[element] = nil}); });
				});
				view.refresh;
			});
			
		};
		
		chainView.mouseUpAction = {|view|
			if(selectedIndex.notNil, { selected[selectedIndex] = false; });
			view.refresh;
		};
		
		window.onClose = { onClose.value(this) };
		
		window.front;
	}
}