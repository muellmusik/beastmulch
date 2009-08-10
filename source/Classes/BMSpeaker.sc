// should this subclass assoc with key = name
BMSpeaker {
	classvar rad2deg, deg2rad;
	var <name; // matches speaker taxonomy we've hashed out
	
	var <>index; // SC output
	
	// cartesian
	var <x, <y, <z; // in meters; for 2D arrays z = 0;
	var <>spec; // instance of SpeakerSpec, contains shared info like freq range, etc.
	
	var <>description; // human readable text
	var <>directivity; // symbol, either 'direct' or 'reflected'
	
	// VBAP style spherical coords, angles (probably in degrees) from a central point
	var <azi; // from median plane +/- 180 deg 
	var <ele; // above azimuthal plane
	var <rad; // in meters from (0, 0, 0), which should be from head height at audience centre
	
	// dBFS cut populated by auto balncing function. This may be arbitrarily low, 
	// so it should only be used for comparison purposes unless normalised across an array
	var <>autoTrim = 0;
	
	*new {|name, index, x = 1, y = 1, z = 1, spec = 'Generic'|
		^super.newCopyArgs(name.asSymbol, index, x, y, z, BMSpeakerSpec.specs[spec.asSymbol]).init;
	}
	
	*newFromSpherical {|name, index, azi = 0, ele = 0, rad = 1, spec = 'Generic'|
		^super.new.initFromSpherical(name, index, azi, ele, rad, BMSpeakerSpec.specs[spec.asSymbol]);
	}
	
	initFromSpherical{|argName, argInd, azimuth, elevation, radius, argSpec|
		name = argName;
		index = argInd;
		azi = azimuth;
		ele = elevation;
		rad = radius;
		spec = argSpec;
		this.calcCartesian;
	}
	
	calcCartesian {
		var azrad, elrad;
		azrad = azi * deg2rad;
		elrad = ele * deg2rad;
		x = rad * cos(elrad) * sin(azrad);
		y = rad * cos(elrad) * cos(azrad);
		z = rad * sin(elrad);
	}
	
	*initClass { 
		rad2deg = 360.0 / ( 2 * pi );
		deg2rad = (2 * pi / 360);	
	}
	
	init {
		azi = atan2(x, y) * rad2deg;
		rad = (x.squared + y.squared + z.squared).sqrt;
		ele = atan2(z, hypot(x, y)) * rad2deg;
	}
//	*newFromSpherical {|azi, ele, rad = 1|
//		^super.new.initFromSpherical(azi, ele, rad = 1);
//	}
//	
//	initFromSpherical{|azimuth, elevation, radius|
//		azi = azimuth;
//		ele = elevation;
//		rad = radius;
//	}
	
	name_ {| newName | 
		name = newName.asSymbol;
	}
		   
	//index_ {| new | index = new }
	x_ {| new | x = new; this.init; this.changed(\newCoordinate) }
	y_ {| new | y = new; this.init; this.changed(\newCoordinate) }
	z_ {| new | z = new; this.init; this.changed(\newCoordinate) }
	
	azi_{| new | azi = new; this.calcCartesian; this.changed(\newCoordinate) }
	ele_{| new | ele = new; this.calcCartesian; this.changed(\newCoordinate) }
	rad_{| new | rad = new; this.calcCartesian; this.changed(\newCoordinate) }
	
	asUGenInput { ^index }
	asControlInput { ^index }
	
	asAssociation {^(name->this) }
	
	isBMSpeaker { ^true }
	
	key {^name }
	
	// post pretty
	printOn { arg stream; stream << this.class.name << "(" <<* [name, index] << ")" }
}

// Wrapper class for managing specs for different speaker models
// speakerspecs are pseudo-singletons: There can only be one of each name

// the only required field is 'name' so any use of these should deal appropriately with nil values
BMSpeakerSpec {
	
	classvar <specs;
	var <name;

	*new { | name, vals | // vals is an event or other Dictionary subclass
		^super.newCopyArgs(name.asSymbol).init((vals ? ()).as(Event));
	}
	
	*newNoInit { |name|
		^super.newCopyArgs(name.asSymbol)
	}
	 
	init { |vals|
		this.class.specs[this.name] = vals;
	}
		
	*initClass {
		 StartUp.add{ 
			 specs = ();
			 
			 // spl is continuous at 1m
			 // plugins is [[specname, presetname], ...]
			 BMSpeakerSpec('Generic', (brand: 'Unknown', minFreq: 20, maxFreq: 20000, spl: nil, powered: false, fullRange: true));
			 BMSpeakerSpec('Generic Sub', (brand: 'Unknown', minFreq: 20, maxFreq: 85, spl: nil, powered: false, fullRange: false));
			 BMSpeakerSpec('SCM50', (brand: 'ATC', minFreq: 38, maxFreq: 20000, spl: 112, powered: false, plugins: [[\Highpass, \atcs]], fullRange: true));
			 BMSpeakerSpec('8030A', (brand: 'Genelec', minFreq: 58, maxFreq: 20000, spl: 97, powered: true, fullRange: true));
			 BMSpeakerSpec('8040A', (brand: 'Genelec', minFreq: 48, maxFreq: 20000, spl: 99, powered: true, fullRange: true));
			 BMSpeakerSpec('8050A', (brand: 'Genelec', minFreq: 38, maxFreq: 20000, spl: 101, powered: true, fullRange: true));
			 BMSpeakerSpec('1037C', (brand: 'Genelec', minFreq: 37, maxFreq: 21000, spl: 107, powered: true, fullRange: true));
			 BMSpeakerSpec('1038B', (brand: 'Genelec', minFreq: 35, maxFreq: 20000, spl: 120, powered: true, fullRange: true));
			 BMSpeakerSpec('1032A', (brand: 'Genelec', minFreq: 42, maxFreq: 21000, spl: 113, powered: true, fullRange: true));
			 BMSpeakerSpec('1037A', (brand: 'Genelec', minFreq: 39, maxFreq: 21000, spl: 106, powered: true, fullRange: true));
			 BMSpeakerSpec('1029A', (brand: 'Genelec', minFreq: 70, maxFreq: 18000, spl: 98, powered: true, fullRange: true));
			 BMSpeakerSpec('7070A', (brand: 'Genelec', minFreq: 19, maxFreq: 85, spl: nil, powered: true, fullRange: false));
			 BMSpeakerSpec('1094A', (brand: 'Genelec', minFreq: 29, maxFreq: 80, spl: nil, powered: true, fullRange: false));
			 BMSpeakerSpec('Circle5', (brand: 'HHb', minFreq: 48, maxFreq: 20000, spl: 87, powered: false, fullRange: true));
			 BMSpeakerSpec('Circle3', (brand: 'HHb', minFreq: 70, maxFreq: 20000, spl: 83, powered: false, fullRange: true));
			 BMSpeakerSpec('Volt', (brand: 'Wilmslow Audio', minFreq: 35, maxFreq: 30000, spl: 88, powered: false, fullRange: true));
			 BMSpeakerSpec('Lynx', (brand: 'Tannoy', minFreq: 50, maxFreq: 20000, spl: 95, powered: false, fullRange: true)); // spl assumes two coupled... thanks Tannoy
			 BMSpeakerSpec('MC24', (brand: 'APG', minFreq: 60, maxFreq: 20000, spl: 99, powered: false, fullRange: true)); // spl @ 1W / 1 meter
			 // KSN1005 nominal spl 95
			 BMSpeakerSpec('Tweeters', (brand: 'Motorola', minFreq: 10000, maxFreq: 27000, spl: nil, powered: false, plugins: [[\Highpass, \tweeters]], fullRange: false));
			 BMSpeakerSpec('Tannoy', (brand: 'Tannoy', minFreq: 40, maxFreq: 20000, spl: 96, powered: false, fullRange: true));
			 BMSpeakerSpec('UREI-809', (brand: 'Urei', minFreq: 50, maxFreq: 17500, spl: 93, powered: false, fullRange: true));
			 BMSpeakerSpec('Kef-C20', (brand: 'Kef', minFreq: 72, maxFreq: 20000, spl: 90, powered: false, fullRange: true));
		 }	
	 }
	 
	// these forward to the appropriate dicts
//	*doesNotUnderstand { arg selector;
//		^if(specs[selector.asSymbol].notNil, {this.newNoInit(selector)}, {super.doesNotUnderstand(selector)});
//	}
	
	doesNotUnderstand { arg selector ... args;
		^this.class.specs[name].perform(selector, *args); // so nil if not there, vals if setter
	}
}


// keep this for now for backwards compatibility
BMSpeakerArray : BMInOutArray {
	
//	add {|speaker|
//		super.add(speaker.name -> speaker);
//	}
//	
//	getSubArray {|name| ^subArrays[name].collectAs({|key| this[key]}, this.class); }
//	
//	isSpeakerArray { ^true } 		
}


//BMSpeakerArrayGUI{
//
//	var outputArray, concertGUI, name, origin;
//	var tempOutputArray, window, windowView;
//	var speakerListCompView, speakerList, instanceVarsBoxes, subarraysWindow;
//	var speakerButtonsView, deleteButton, upButton, downButton, importPopUpMenu, okButton;
//	var >onClose;
//	
//	
//	*new {| outputArray, concertGUI, name, origin |
//		  ^super.newCopyArgs(outputArray, concertGUI, name)
//		  	.init.makeWindow(origin ? (40@200));
//	}
//	
//	init {
//		this.makeTempOutArray(outputArray);
//	}
//	
//	makeWindow {| origin |
//	
//	   var x, y, numTypes, specsList, speakerVarsView;
//	   
//	   x = origin.x;
//	   y = origin.y;
//		
//	   window		= SCWindow(name, Rect.new(x, y, 496, 554+31+10), false);
//	   window.view.decorator = FlowLayout(window.view.bounds);
//	   specsList	= SCScrollView(window, Rect(0, 0, 160, 508))
//				   .hasHorizontalScroller_(false)
//				   .hasBorder_(true);
//	   numTypes	= BMSpeakerSpec.specs.size;
//	   specsList	= SCVLayoutView(specsList, Rect(4,4,150, numTypes * 24 + 4));
//	   
//	   BMSpeakerSpec.specs.keysDo({|spName|Ê
//			SCDragSource(specsList, Rect(0, 0, 150, 20)).string_(" Ê " ++ spName.asString)
//				.background_(Color.grey.alpha_(0.2))
//				.font_(Font("Helvetica-Bold", 12))
//				.beginDragAction_({ BMSpeaker(spec: spName) })
//		});
//	   
//	   speakerListCompView	= SCCompositeView(window, 160 @ 508)
//						   .background_(Color.grey.alpha_(0.3));
//	   speakerListCompView.decorator = FlowLayout(speakerListCompView.bounds);
//	   speakerList	= SCListView(speakerListCompView, 152 @ (508-35))
//	 					.items_(tempOutputArray.keys.asArray)
//	 					.action_({| view | 
//	 						var speaker	= tempOutputArray[view.item];
//		 					if ((tempOutputArray.size > 0))
//				    	  	    	   { if (speaker.isBMSpeaker)
//			 					   { instanceVarsBoxes
//			 						 .keysValuesDo{| key | 
//			 						 	var value;
//			 						 	value = speaker.perform(key);
//			 						 	if (key == \index) { value = value + 1  };
//			 						 	instanceVarsBoxes[key].value = value };
//			 					   }
//			 					   {  
//			 					     instanceVarsBoxes
//			 						 .keysValuesDo{| key | 
//			 						 	if (key == \name) 
//			 						 		{ instanceVarsBoxes[key].value = view.item }
//			 						 		{ instanceVarsBoxes[key].value = "" }
//			 						 };
//			 					   }
//			 				   }
//			 					 						       				   { instanceVarsBoxes
//			 						 .keysValuesDo{| key | 
//			 						 	instanceVarsBoxes[key].value = "" 
//			 						 };
//			 				   }
//	 					});
//	   
//	
//	 	speakerList.keyDownAction = { arg view,char,modifiers,unicode,keycode;
//	 		block { |break|
//				if((modifiers == 11534600) && (unicode == 63233), {
//					downButton.doAction;
//					break.value;
//				});
//				if((modifiers == 11534600) && (unicode == 63232), {
//					upButton.doAction;
//					break.value;
//				});
//				if(unicode == 127, { deleteButton.doAction });
//				speakerList.defaultKeyDownAction(char,modifiers,unicode);
//			}
//		};
//
//		speakerList.canReceiveDragHandler = { SCView.currentDrag.isKindOf(BMSpeaker) };
//		speakerList.receiveDragHandler = { var newSpeaker = SCView.currentDrag; 
//									  this.makeNewSpeakerWindow(newSpeaker)
//									};
//
//
//
//// List's Buttons ---------------------
//
//		deleteButton				= RoundButton(speakerListCompView, 20 @ 20).extrude_(false).canFocus_(false);		deleteButton.states		= [[ '-', Color.black,  Color.white.alpha_(0.8) ]];
//		deleteButton.action		= { var viewIndex;
//							   	    if (speakerList.item.notNil)
//							    	  	  { viewIndex = speakerList.value;
//							    	  	    tempOutputArray.removeAt(speakerList.item);
//							    	  	    if ((viewIndex == (tempOutputArray.size)) and: { tempOutputArray.size > 0 })
//							    	  	    	   { speakerList.valueAction = viewIndex - 1 }
//							    	  	    	   { speakerList.value_(viewIndex).doAction }
//							    	  	  }
//							       };
//	
//		speakerListCompView.decorator.shift(6, 0);
//		upButton					= RoundButton(speakerListCompView, 20 @ 20).extrude_(false).canFocus_(false);		upButton.states			= [[ \up, Color.black,  Color.white.alpha_(0.8) ]];
//		upButton.action 			= { var index;
//							    	    index 	= tempOutputArray.keys.indexOf(speakerList.item);
//								    if (index.notNil) { tempOutputArray.moveSpeakerUp(index) }
//							 	  };
//
//		speakerListCompView.decorator.shift(-3, 0);
//		
//		downButton				= RoundButton(speakerListCompView, 20 @ 20).extrude_(false).canFocus_(false);		downButton.states 			= [[ \down, Color.black,  Color.white.alpha_(0.8) ]];
//		downButton.action 			= { var index;
//								    index 	= tempOutputArray.keys.indexOf(speakerList.item);
//								    if (index.notNil) { tempOutputArray.moveSpeakerDown(index) }								  };
//
//
//		// instance variables
//		speakerVarsView	= SCCompositeView(window, 160 @ 508).background_(Color.grey.alpha_(0.3));
//		speakerVarsView.decorator = FlowLayout(speakerVarsView.bounds, Point(10, 10), Point(4, 10));
//		
//		instanceVarsBoxes	= [ \name -> "Name", \index -> "Output", 
//						    \x -> "x", \y -> "y", \z -> "z", \azi -> "azimuth", \ele -> "elevation", \rad -> "radius" ]
//						  .collectAs({| instVar |
//						  	
//						  	SCStaticText(speakerVarsView, 54 @ 20).string_(instVar.value ++ ":").font_(Font("Helvetica", 12));
//						  	if (instVar.key == \name)
//						  	   {	instVar.value = SCTextField(speakerVarsView, 82 @ 20);
//						  	   	instVar.value.action_{| view |
//							  	   			var speaker = tempOutputArray[speakerList.item], name = view.value;
//							  	   			if (tempOutputArray.keys.any{| nameInList | nameInList == name.asSymbol }
//							  	   			    and: {tempOutputArray.keys.indexOfEqual(name.asSymbol) != speakerList.value })
//					   			        		   { BMAlert( "The name \"" ++ name ++ "\" is already taken. Please choose a different name.", 
//					   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
//					   			        			 background: Color.white,
//					   			        			 color: Color.red,
//					   			        			 border: false
//					   			        	 		);
//					   			        	 		view.value = speaker.name;
//					   			        	 	    }
//					   			        	 	    { speaker.perform(instVar.key.asSetter, name) }
//				   			           }
//						  	   }
//						  	   { instVar.value = SCNumberBox(speakerVarsView, 82 @ 20);
//						  	     instVar.value.action_({| view | 
//						  				 		var speaker = tempOutputArray[speakerList.item], value = view.value;
//						  				 		if (instVar.key == \index) { value = value - 1 };
//						  				 		speaker.perform(instVar.key.asSetter, value);
//						  					 })
//						  	   };
//						  	   instVar.value.background_(Color.white.alpha_(0.3)).font_(Font("Helvetica", 12));
//						  	 	
//						  	
//						  	speakerVarsView.decorator.shift(0, 10);
//						  	instVar
//						   },
//						   Event
//						   );
//		
//		speakerList.doAction;
//		speakerVarsView.decorator.shift(0, 86); 
//		RoundButton(speakerVarsView, 140 @ 20)
//			   .extrude_(false).canFocus_(false) 
//			   .states_([[ "Subarrays", Color.black, Color.white.alpha_(0.8) ]])
//			   .action_({ 
//			   	if (subarraysWindow.isNil) 
//			   		{ subarraysWindow = BMSubarrayMenuGUI(tempOutputArray, "Define Subarray");
//			   		  subarraysWindow.onClose_({ subarraysWindow = nil })
//			   		}
//			   });
//		speakerVarsView.decorator.shift(0, 5); 
//		SCStaticText.new(speakerVarsView, 140 @ 20).string = "Import / Export:";
//		
//		importPopUpMenu = SCPopUpMenu(speakerVarsView, 140 @ 20)
//					   .items_([ " ", "Import Speaker Array", "Export Speaker Array" ])
//					   .background_(Color.white)
//					   .action_({| view |
//					   	switch(view.value,
//					   		// import speaker array
//					   		1, { CocoaDialog.getPaths({| path | 
//								var recalledOutputArray;
//								
//								recalledOutputArray = Object.readTextArchive(path[0]);
//								if (subarraysWindow.notNil) { subarraysWindow.window.close };
//								this.removeTempOutputArrayDependants;
//								this.makeTempOutArray(recalledOutputArray);
//								speakerList.value_(0).doAction;
//								tempOutputArray.changed;
//								}, maxSize: 1);
//
//							 },
//					   		
//					   		// export speaker array
//					   		2, { CocoaDialog.savePanel({| path | 
//							 	tempOutputArray.writeTextArchive(path);
//							 	tempOutputArray.storeSpeakerArray;
//							 	concertGUI.concertManager.storeSession(concertGUI.configManager);
//							    })
//					  		 }
//					      );
//					       	
//					  	 view.value = 0
//					   		
//					   	});
//		
//		window.view.decorator.nextLine;
//		window.view.decorator.shift(0, 8);
//		
//		SCStaticText(window, 488 @ 16)
//		 .string_("Drag speaker model from left to create a new speaker")
//		 .font_(Font("Helvetica-Bold", 12));
//		window.view.decorator.nextLine;
//		window.view.decorator.shift(250, 10);
//		RoundButton(window, 115 @ 20)
//			   .extrude_(false).canFocus_(false) 
//			   .states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
//			   .action_({	window.close });
//		
//		okButton = RoundButton(window, 115 @ 20)
//				   .extrude_(false).canFocus_(false)
//				   .states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
//				   .action_({ concertGUI.configManager.currentConfig_('all off', \concertEditor);
//				   			concertGUI.chain[0].do({|el|
//       							el.free; // clean me up
//       							el.release; // remove from BMAbstractAudioChainElement's dict
//							});
//							concertGUI.chain.copyToEnd(1).do({|el|
//       							el.free; // clean me up
//       							el.release; // remove from BMAbstractAudioChainElement's dict
//							});
//				   			this.updateOutputArray(tempOutputArray);
//				   			this.rebuildChain;
//				   			window.close;
//				   			concertGUI.listViewSelection(false);
//				   			concertGUI.concertManager.storeSession(concertGUI.configManager);
//				   });
//				   
//		window.onClose = { 
//			if (subarraysWindow.notNil) { subarraysWindow.window.close };
//			this.removeTempOutputArrayDependants;
//			onClose.value(this)
//		};
//		window.front;
//		}
//		
//		makeTempOutArray {| array | 
//			tempOutputArray = array.deepCopy;
//			tempOutputArray.array.do{| assoc | assoc.value.addDependant(tempOutputArray) };
//			tempOutputArray.addDependant(this);
//		}
//		
//		removeTempOutputArrayDependants {
//			tempOutputArray.array.do{| assoc |  assoc.value.removeDependant(tempOutputArray) };
//			tempOutputArray.removeDependant(this); 
//		}
//		
//		updateOutputArray {| newChain |
//			outputArray.subArrays.copy.do{| key | outputArray.removeSubArray(key) };
//			outputArray.keys.copy.do{| key |  outputArray.removeAt(key) };
//   		     
//   		     newChain.keys.do{| key | outputArray.add(newChain[key].deepCopy) };
//   		     newChain.subArrays.do{| key | 
//   		     	outputArray.defineSubArray(key, newChain.getSubArrayKeys(key).deepCopy) 
//   		     };
//   		     outputArray.storeSpeakerArray;
//		}
//		
//		rebuildChain {
//			concertGUI.chain = concertGUI.chainFunc.value;
//			concertGUI.configsGUIFunc.value(concertGUI.chain); 
//		}
//		
//		update {| changed, change |
//		 	if (change != \newCoordinate) { speakerList.items_(tempOutputArray.keys.asArray) }; 
//		 	switch(change,
//		 		\moveDown, { speakerList.value = speakerList.value + 1 },
//		 		\moveUp, { speakerList.value = speakerList.value - 1 }
//		 	);
//		 	speakerList.doAction;
//		 }
//		 
//	makeNewSpeakerWindow {| newSpeaker, origin |
//
//		var window, name, speakerNameField, okButton, speakerIndexField;
//		 
//		origin		= origin ?? { 490 @ 500 };
//		window 		= SCWindow("New Speaker", Rect(origin.x, origin.y, 260, 110 + 30), false).userCanClose_(false);
//		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
//		
//		SCStaticText(window, 50 @ 20).string = "Name:";
//
//		speakerNameField	= SCTextView(window, 180 @ 20)
//							.hasVerticalScroller_(false)
//							.hasHorizontalScroller_(false)
//							.enterInterpretsSelection_(false);
//		
//		SCStaticText(window, 50 @ 20).string = "HW Out:";
//		speakerIndexField	= SCTextView(window, 180 @ 20)
//							.hasVerticalScroller_(false)
//							.hasHorizontalScroller_(false)
//							.enterInterpretsSelection_(false);
//					
//		window.view.decorator.shift(0, 30);
//		
//		RoundButton(window, 115 @ 20)
//			   .extrude_(false).canFocus_(false) 
//			   .states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
//			   .action_({	window.close });
//			   
//		okButton = RoundButton(window, 115 @ 20)
//				   .extrude_(false).canFocus_(false)
//				   .states_([[ "Create", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
//				   .action_({ var name;
//				   			
//				   			name = speakerNameField.string;
//				   			if (name.size > 0) 
//				   				{ name = name.asSymbol;
//				   				  if (tempOutputArray.keys.any{| nameInList | nameInList == name })
//				   			        	{ BMAlert("The name \"" ++ name ++ "\" is already taken. Please choose a different name.", 
//				   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
//				   			        			 background: Color.white,
//				   			        			 color: Color.red,
//				   			        			 border: false
//				   			        	 ) 
//				   			          }
//				   			          {window.close;
//					   				  newSpeaker.name = name;
//					   				  newSpeaker.index = speakerIndexField.string.asInteger - 1;
//									  tempOutputArray.add(newSpeaker);
//									  speakerList.value_(tempOutputArray.lastIndex).doAction;
//				   				 	 }
//				   				 }
//				   		   });
//		speakerNameField.focus;
//		window.front
//	}		 
//}
//
//
//BMSubarrayMenuGUI : BMAbstractGUI {
//
//	var tempOutputArray;
//	var assigns, assignSection, assignButton, assignView, newButton;
//	var subarrays, subarraySection, subarrayView, speakerSection, speakerView;
//	var newButton, deleteButton, addButton, upButton, downButton;
//	var labelPlusButton, matrixButton, clearButton, buttonSection;
//	
//	*new {|tempOutputArray, name, origin|
//		^super.new.init(tempOutputArray, name).makeWindow(origin ? (40@200));
//	}
//	
//	init { |argtempOutputArray, argname |
//		tempOutputArray 	= argtempOutputArray;
//		name				= argname;
//		tempOutputArray.addDependant(this);
//		assigns			= List.new;
//	}
//	
//	makeWindow { |origin|
//		var x, y;
//		x = origin.x;
//		y = origin.y;
//		
//		window = SCWindow(name, Rect.new(x, y, 800-20, 300), false);
//		window.view.decorator = FlowLayout(window.view.bounds);
//		subarraySection	= SCCompositeView(window, 200 @ 281).background_(Color.grey.alpha_(0.3));
//		
//		subarraySection.decorator = FlowLayout(subarraySection.bounds);
//		SCStaticText.new(subarraySection, Rect(0,0,180,20)).font_(Font("Helvetica-Bold", 14))
//			.string = "Subarrays";
//		subarrayView	= SCListView(subarraySection, (200-8) @ (250 - 25))
//	 					.items_(tempOutputArray.subArrays.asArray)
//	 					.action_({ speakerView.value_(0); assignView.value_(0); this.update });
//
//		subarraySection.decorator.nextLine;
//	
//		newButton				= RoundButton(subarraySection, 20 @ 20).extrude_(false).canFocus_(false)
//					 		  .font_(Font("Arial", 11)).states_([['+', Color.black,  Color.white.alpha_(0.8) ]])
//					 		  .action_({ this.makeNewSubarrayWindow(490 @ 500) });
//		deleteButton			= RoundButton(subarraySection, 20 @ 20).extrude_(false).canFocus_(false);		deleteButton.states	= [[ '-', Color.black,  Color.white.alpha_(0.8) ]];
//		deleteButton.action	= { var viewIndex, name;
//							    if (subarrayView.item.notNil)
//							  	  { viewIndex = subarrayView.value;
//							  	    name = subarrayView.item;
//							  	    if ((viewIndex == (tempOutputArray.subArrays.lastIndex)) and: { tempOutputArray.subArrays.size > 1 })
//							  	    	   { subarrayView.value_(viewIndex - 1) }
//							  	    	   { subarrayView.value_(viewIndex) };
//							  	    tempOutputArray.removeSubArray(name.asSymbol);
//							  	  }
//							   };
//
//		
//		assignSection = SCCompositeView(window, Rect(0, 0, 200, 281)).background_(Color.grey.alpha_(0.3));
//		assignSection.decorator = FlowLayout(assignSection.bounds);
//		SCStaticText.new(assignSection, Rect(0,0,180,20)).font_(Font("Helvetica-Bold", 14))
//			.string = "Assignments";
//		assignView = SCListView(assignSection, Rect(0, 0, 200-8, 250-1)).canReceiveDragHandler = true;
//		assignView.receiveDragHandler = { 
//			tempOutputArray.addToSubArray(subarrayView.item.asSymbol, SCView.currentDrag.asSymbol)
//		};
//		assignView.keyDownAction = { arg view,char,modifiers,unicode,keycode;
//			var viewIndex, name, assigns;
//			if(unicode == 127 and: { subarrayView.item.notNil }) 
//			   { viewIndex = assignView.value;
//			     name = assignView.item;
//			     assigns = tempOutputArray.getSubArrayKeys(subarrayView.item.asSymbol);
//			     if ((viewIndex == assigns.lastIndex) and: { assigns.size > 1 })
//							  	    	   { assignView.value_(viewIndex - 1) }
//							  	    	   { assignView.value_(viewIndex) };
//			   	tempOutputArray.removeFromSubArray(subarrayView.item.asSymbol, name.asSymbol) }
//			
//		};
//	
//			
//		speakerSection = SCCompositeView(window, Rect(0, 0, 200, 281)).background_(Color.grey.alpha_(0.3));
//		speakerSection.decorator = FlowLayout(speakerSection.bounds);
//		SCStaticText.new(speakerSection, Rect(0,0,75,20)).font_(Font("Helvetica-Bold", 14))
//			.string = "Speakers";
//		assignButton = SCButton(speakerSection, Rect(0,0,110,20)).canReceiveDragHandler = false;		assignButton.states = [["<", Color.black,Color.clear]];
//		assignButton.action = { 
//			if (subarrayView.item.notNil and: { speakerView.item.notNil })
//				{ tempOutputArray.addToSubArray(subarrayView.item.asSymbol, speakerView.item.asSymbol) }
//		};	
//		speakerView = SCListView(speakerSection, Rect(0, 0, 200-8, 250-1)).canReceiveDragHandler = false;
//		speakerView.beginDragAction = {|view| view.item };
//		buttonSection = SCVLayoutView(window, Rect(0, 0, 155, 300));
//		SCStaticText.new(buttonSection, Rect(0,0,80,24)).string_(" ");// placeholder
//		clearButton = SCButton(buttonSection, Rect(0,0,110,20)).canReceiveDragHandler = false;		clearButton.states = [["Clear Assignments", Color.black,Color.clear]];
//		clearButton.action = { if (subarrayView.item.notNil) { tempOutputArray.defineSubArray(subarrayView.item.asSymbol, []) } };
//		
//		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
//		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
//		
//		SCStaticText.new(buttonSection, Rect(0,0,80,110))
//		 .string_("Assign speakers to selected subarray. Cmd-drag or use button to add, select and press delete to remove.");
//		this.update;
//		
//		window.onClose = { 
//			tempOutputArray.removeDependant(this); 
//			onClose.value(this)
//		};
//		window.front;
//	}
//	
//	update {
//		subarrayView.items = tempOutputArray.subArrays;
//		if (tempOutputArray.subArrays.size > 0) 
//			{ assignView.items  = tempOutputArray.getSubArrayKeys(subarrayView.item.asSymbol);
//			  speakerView.items = tempOutputArray.keys
//			  			   		.difference(tempOutputArray.getSubArrayKeys(subarrayView.item.asSymbol));
//		     }
//			{ assignView.items = [];
//			  speakerView.items = tempOutputArray.keys
//			}
//	}
//	
//	makeNewSubarrayWindow {| origin |
//
//		var window, name, subarrayNameField, okButton;
//		 
//		origin		= origin ?? { 490 @ 500 };
//		window 		= SCWindow("New Subarray", Rect(origin.x, origin.y, 260, 110), false).userCanClose_(false);
//		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
//		
//		SCStaticText(window, 50 @ 20).string = "Name:";
//
//		subarrayNameField	= SCTextView(window, 180 @ 20)
//							.hasVerticalScroller_(false)
//							.hasHorizontalScroller_(false)
//							.enterInterpretsSelection_(false);
//					
//		window.view.decorator.shift(0, 30);
//		
//		RoundButton(window, 115 @ 20)
//			   .extrude_(false).canFocus_(false) 
//			   .states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
//			   .action_({	window.close });
//			   
//		okButton = RoundButton(window, 115 @ 20)
//				   .extrude_(false).canFocus_(false)
//				   .states_([[ "Create", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
//				   .action_({ var name;
//				   			
//				   			name = subarrayNameField.string;
//				   			if (name.size > 0) 
//				   				{ name = name.asSymbol;
//				   				  if (tempOutputArray.subArrays.any{| nameInList | nameInList == name })
//				   			        	{ BMAlert("The name \"" ++ name ++ "\" is already taken. Please choose a different name.", 
//				   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
//				   			        			 background: Color.white,
//				   			        			 color: Color.red,
//				   			        			 border: false
//				   			        	 ) 
//				   			          }
//				   			          { window.close;
//				   			            tempOutputArray.defineSubArray(name, []);
//				   			            subarrayView.value_(tempOutputArray.subArrays.lastIndex).doAction;
//				   				 	}
//				   				 }
//				   	});
//		subarrayNameField.focus;
//		window.front
//	}
//}
