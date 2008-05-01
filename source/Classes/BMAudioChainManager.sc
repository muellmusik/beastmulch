BMConfigurations {// move this classe to the signal chain file
	var <dict, <names, <currentConfig;

	*new {
	
		  ^super.new.init;
	
	}

	init { 
		   
		   this.clear;
		   CmdPeriod.add(this) 
		   
	}
		  
	clear { 
	
		   dict = IdentityDictionary[];
		   names = List[];
		   
	}
	
	dict_{| x |
	 
		 dict = x;
		 this.changed(\dict);
		 
	}
	
	names_{| x |
	 
		 names = x;
		 this.changed(\names)
		 
	}
	
	currentConfig_{| configName, from |
		 "currentConfig was called".postln;
		 currentConfig = configName;
		 this.loadConfig(configName);
		 this.changed(\currentConfig, configName, from)
	}
	 
	add {| configuration, indexInNamesList |
		
		     if (indexInNamesList.notNil)
		   	   { names.insert(indexInNamesList + 1, configuration.key);
		   	     dict.add(configuration) }
		   	   { if (names.indexOfEqual(configuration.key).isNil) { names.add(configuration.key) };
		   	     dict.add(configuration);
		   	    };
		   	
		   	this.changed(\add, configuration.key);
	   
	}
	
	removeAt {| configurationIndex |
		    
		    dict.removeAt(names[configurationIndex]);
		    names.removeAt(configurationIndex);
		    this.changed(\removeAt)
		    
	}
	
	

	loadConfig {| configName |
		   "loadConfig was called".postln;
   		   BMAbstractAudioChainElement
			 .allChainElements
			 .keysValuesDo{| key, value |
			 			 BMAbstractAudioChainElement.allChainElements[key].mappings = this.dict[configName][key]
			 };
		   (configName.asString ++ " was loaded").postln
		   	 
	
	}
	
}


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
	var <manager, configurations, name, guis, objects;
	var chainView;
	var configView, configViewWidth, chainView, configListView;
	var newButton, copyButton, deleteButton, storeButton, upButton, downButton;
	
	*new {|manager, configurations, name, origin|
		^super.new.init(manager, configurations, name ? "Signal Chain").makeWindow(origin ? (250@550));
	}
	
	init {|argManager, argConfigurations, argName|
		manager = argManager;
		configurations = argConfigurations;
		name = argName;
		guis = IdentityDictionary.new; // use Objects as keys
		configurations.addDependant(this);
	}

	makeWindow {|origin|
		var x, y, rows, columns, width, pseudoLevels, pseudoTimes, count = 0, selected;
		var points, rects, selectedIndex;
		

		
		x 			= origin.x;
		y			= origin.y;
		objects 		= manager.sources 
					  ++ [manager.sourceProcessing, manager.audioMatrix, manager.outputProcessing].flat;
		selected 		= false ! objects.size;
		rows 		= objects.size - manager.sources.size + 1;
		columns		= manager.sources.size;
		width 		= max(450, columns * 150);
		
		pseudoLevels 	= (1..rows).normalize * 0.8 + 0.1;
		pseudoLevels 	= pseudoLevels.collect({|item, i| if(i == 0, {item ! columns}, {item})}).flat;
		pseudoTimes 	= [(1..columns).normalize - 0.5 * 0.68 + 0.5, 0.5 ! (rows - 1)].flat;
		
		configViewWidth = 210;
		
		window = SCWindow(name, Rect.new(x, y, 450 + configViewWidth + 27, 450 + 6), false);
		window.view.decorator = FlowLayout(window.view.bounds);
		window.userCanClose_(false);
		
		
		configView			= SCCompositeView(window, configViewWidth @ 435);
		configView.decorator 	= FlowLayout(configView.bounds, Point(5, 5), Point(5, 5));
		configView.background	= Color.white.alpha_(0.2);
		SCStaticText(configView, 180 @ 20).string_("Configurations");
		
		configListView		= SCListView(configView, 200 @ 373).canReceiveDragHandler = false;
		configListView.background_(Color.white).hiliteColor_(Color.new255(51, 111, 203, 255 * 0.95));

		configListView.action	= {| view |
							   "configListView.action".postln;
							   [ view.value, view.item ].postln;
							   configurations.currentConfig_(view.item, \configurationEditor);
								 
							  };

		configView.decorator.nextLine;
	
		newButton				= RoundButton(configView, 20 @ 20).extrude_(false).canFocus_(false)
					 		  .font_(Font("Arial", 11)).states_([['+', Color.black,  Color.white.alpha_(0.8) ]])
					 		  .action_({ this.makeNewConfigWindow("New", 490 @ 500) });
					 		  
		configView.decorator.shift(-3, 0);
		
		copyButton			= RoundButton(configView, 46 @ 20).extrude_(false).canFocus_(false)
					 		  .font_(Font("Arial", 11)).states_([["Copy", Color.black,  Color.white.alpha_(0.8) ]])
					 		  .action_({ this.makeNewConfigWindow("Copy", 490 @ 500) });
		
		configView.decorator.shift(-3, 0);			 		  
		deleteButton			= RoundButton(configView, 20 @ 20).extrude_(false).canFocus_(false)
					 		  .font_(Font("Arial", 11)).states_([['-', Color.black,  Color.white.alpha_(0.8) ]]);
			 		  
		deleteButton.action	= { var viewIndex, name;
						   	    
						   	    viewIndex 	= configListView.value;
						    	    name			= configListView.item;
						    	    
							    if (name != 'all off')
							    	   { configurations.removeAt(configurations.names.indexOf(name));
							    	     if ((viewIndex == (configurations.names.size)) and: { configurations.names.size > 0 })
							    	     	{ configListView.value = viewIndex - 1 }
					    	  	    	   		{ if ((viewIndex > 0).postln) { configListView.value(viewIndex) }};
					    	  	    	     configurations.currentConfig_(configListView.item, \configurationEditor);
					    	  	    	   }
						    	  	   
						       };
						       
		configView.decorator.shift(6, 0);

		upButton				= RoundButton(configView, 20 @ 20).extrude_(false).canFocus_(false);		upButton.states		= [[ \up, Color.black,  Color.white.alpha_(0.8) ]];
		upButton.action 		= { var index;
						    
							    index 	= configurations.names.indexOf(configListView.item);
							    if (index.notNil and: {index > 0 })
							    	   { configurations.names = configurations.names.swap(index - 1, index);
							   
						    	          configListView.value = index - 1
							    	   }
						 	  };

		configView.decorator.shift(-3, 0);
	
		downButton			= RoundButton(configView, 20 @ 20).extrude_(false).canFocus_(false);		downButton.states 		= [[ \down, Color.black,  Color.white.alpha_(0.8) ]];
		downButton.action 		= { var index;
		
							    index 	= configurations.names.indexOf(configListView.item);
							    if (index.notNil and: { index < (configurations.names.size - 1) })
							    	  { configurations.names = configurations.names.swap(index, index + 1);
							    	    configListView.value = index + 1
							    	  }
							  };
				 		  
		configView.decorator.shift(6, 0); 				
		storeButton			= RoundButton(configView, 46 @ 20).extrude_(false).canFocus_(false)
				 		 	 .font_(Font("Arial", 11)).states_([["Store", Color.black,  Color.white.alpha_(0.8) ]])
				 		 	 .action_({ var name;
				
								        name = configListView.item;
								        if (name != 'all off')
								           {  configurations.dict[name] = IdentityDictionary[];
											BMAbstractAudioChainElement.allChainElements
											 .keysValuesDo{| key, value |
									 			 configurations.dict[name].add(key -> value.mappings.deepCopy)
									 		  }
								 		  }
								 		  { "This Configuration cannot be modified".error }
								    						
					     	  });


		chainView				= SCScrollView(window, 465 @ 435).hasBorder_(false);
		if(width <= 465, { chainView.hasHorizontalScroller = false });
		chainView 			= SCCompositeView(chainView, Rect(0, 0, width, max(450, rows * 80)));
		chainView.background 	= Color.white.alpha_(0.2);
		chainView 			= SCUserView(chainView, Rect(0, 0, width, max(450, rows * 80)));
		
		
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
		
		
		
		this.update;
		window.onClose = { onClose.value(this) };
		window.front;
	}
	
	makeNewConfigWindow {| method, origin |

		var window, name, pieceNameField, okButton;
		 
		window 			= SCWindow(method + "Configuration", Rect(origin.x, origin.y, 260, 110), false).userCanClose_(false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		SCStaticText(window, 50 @ 20).string = "Name:";

		pieceNameField	= SCTextView(window, 180 @ 20)
							.keyDownAction_({|view, key| if ((key == 3.asAscii) || (key == $\r) || (key == $\n), { view.doAction })})
							.string_("")
							.action_({ pieceNameField.string.postln })
							.hasVerticalScroller_(false)
							.hasHorizontalScroller_(false)
							.enterInterpretsSelection_(false);
					
		window.view.decorator.shift(0, 30);
		
		RoundButton(window, 115 @ 20)
			   .extrude_(false).canFocus_(false) 
			   .states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
			   .action_({	window.close });
			   
		okButton = RoundButton(window, 115 @ 20)
				   .extrude_(false).canFocus_(false)
				   .states_([[ "Create", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
				   .action_({ var name;
				   			
				   			name = pieceNameField.string;
				   			if (name.size > 0) 
				   				{ name = name.asSymbol;
				   				  if (configurations.names.any{| nameInList | nameInList == name })
				   			        	{ BMAlert( "The name \"" ++ name ++ "\" is already taken. Please choose a different name.", 
				   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
				   			        			 background: Color.clear,
				   			        			 color: Color.red,
				   			        			 border: false
				   			        	 ) 
				   			          }
				   			          { 
				   				
				   				 window.close;
				   				  if (method == "New")
				   				  	{ configurations.add(name -> configurations.dict['all off'].deepCopy, 
				   				  				       configListView.value
				   				  	  ) 
				   				  	}
				   				  	{ configurations.add(name -> configurations.dict[configListView.item].deepCopy,
				   				  				       configListView.value
				   				  	  ) 
				   				  	};
				   				   configListView.value = configListView.value + 1;
				   				   configurations.currentConfig_(name, \configurationEditor);
				   				 }
				   				 
				   				 
				   				 }
				   				 

				   		   });
		pieceNameField.focus;
		window.front
	}
	
	update {| changed, change, configName, from |
			"configuration window' update function called".postln;
			[ changed, change, configName ].postln;
	 		
	         ("configListView.value at the beginning of update" +configListView.value).postln;
	         configListView.items 		= configurations.names.asArray;
	         
	         if ((change == \currentConfig) and: { from == \concertEditor }) 
	         	   { "if currentConfig and from concertEditor".postln; configListView.value = configurations.names.indexOf(configName) };
	         ("configListView.value at the end of update" +configListView.value).postln;

	}
	
}