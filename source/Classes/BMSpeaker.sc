BMSpeaker {
	classvar rad2deg;
	var <name; // matches speaker taxonomy we've hashed out
	
	var <index; // SC output
	
	// cartesian
	var <x, <y, <z; // in meters; for 2D arrays z = 0;
	var <>spec; // instance of SpeakerSpec, contains shared info like freq range, etc.
	
	var <>description; // human readable text
	var <>directivity; // symbol, either 'direct' or 'reflected'
	
	// VBAP style spherical coords, angles (probably in degrees) from a central point
	var <>azi; // from median plane +/- 180 deg 
	var <>ele; // above azimuthal plane
	var <>rad; // in meters from (0, 0, 0), which should be audience centre
	
	// dBFS cut populated by auto balncing function. This may be arbitrarily low, 
	// so it should only be used for comparison purposes unless normalised across an array
	var <>autoTrim = 0;
	
	*new {|name, index, x = 1, y = 1, z = 1, spec|
		^super.newCopyArgs(name, index, x, y, z, BMSpeakerSpec.specs[spec.asSymbol]).init;
	}
	
	*initClass { rad2deg = 360.0 / ( 2 * pi );}
	
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
		   var oldName = name; 
		   name = newName.asSymbol; 
		   this.changed(\rename, oldName, name) 
		   }
	index_ {| new | index = new }
	x_ {| new | x = new; this.init }
	y_ {| new | y = new; this.init }
	z_ {| new | z = new; this.init }
	
	asUGenInput { ^index }
	asControlInput { ^index }
	
	asAssociation {^(name->this) }
	
	isBMSpeaker { ^true }
}

// Wrapper class for managing specs for different speaker models
// speakerspecs are pseudo-singletons: There can only be one of each name

// the only required field is 'name' so any use of these should deal appropriately with nil values
BMSpeakerSpec {
	
	classvar <specs;
	var <name;

	*new { | name, vals | // vals is an event or other Dictionary subclass
		^super.newCopyArgs(name.asSymbol).init(vals.as(Event));
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
			 BMSpeakerSpec('SCM50', (brand: 'ATC', minFreq: 38, maxFreq: 20000, spl: 112, powered: false, plugins: [[\highpass, \atcs]]));
			 BMSpeakerSpec('8030A', (brand: 'Genelec', minFreq: 58, maxFreq: 20000, spl: 97, powered: true));
			 BMSpeakerSpec('8040A', (brand: 'Genelec', minFreq: 48, maxFreq: 20000, spl: 99, powered: true));
			 BMSpeakerSpec('8050A', (brand: 'Genelec', minFreq: 38, maxFreq: 20000, spl: 101, powered: true));
			 BMSpeakerSpec('1037C', (brand: 'Genelec', minFreq: 37, maxFreq: 21000, spl: 107, powered: true));
			 BMSpeakerSpec('1037A', (brand: 'Genelec', minFreq: 39, maxFreq: 21000, spl: 106, powered: true));
			 BMSpeakerSpec('1029A', (brand: 'Genelec', minFreq: 70, maxFreq: 18000, spl: 98, powered: true));
			 BMSpeakerSpec('7070A', (brand: 'Genelec', minFreq: 19, maxFreq: 85, spl: nil, powered: true));
			 BMSpeakerSpec('1094A', (brand: 'Genelec', minFreq: 29, maxFreq: 80, spl: nil, powered: true));
			 BMSpeakerSpec('Circle5', (brand: 'HHb', minFreq: 48, maxFreq: 20000, spl: 87, powered: false));
			 BMSpeakerSpec('Circle3', (brand: 'HHb', minFreq: 70, maxFreq: 20000, spl: 83, powered: false));
			 BMSpeakerSpec('Volt', (brand: 'Wilmslow Audio', minFreq: 35, maxFreq: 30000, spl: 88, powered: false));
			 BMSpeakerSpec('Lynx', (brand: 'Tannoy', minFreq: 50, maxFreq: 20000, spl: 95, powered: false)); // spl assumes two coupled... thanks Tannoy
			 BMSpeakerSpec('MC24', (brand: 'APG', minFreq: 60, maxFreq: 20000, spl: 99, powered: false)); // spl @ 1W / 1 meter
			 // KSN1005 nominal spl 95
			 BMSpeakerSpec('Tweeters', (brand: 'Motorola', minFreq: 10000, maxFreq: 27000, spl: nil, powered: false, plugins: [[\highpass, \tweeters]]));
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


BMSpeakerArrayGUI{

	var outputArray, name, window, windowView;
	var speakerListCompView, speakerList, instanceVarsBoxes;
	var speakerButtonsView, deleteButton, upButton, downButton, storeButton, importPopUpMenu;
	var >onClose;
	
	
	*new {| outputArray, name, origin |
		  ^super.newCopyArgs(outputArray, name).init.makeWindow(origin ? (40@200));
	}
	
	init {
		outputArray.addDependant(this);
	}
	
	makeWindow {| origin |
	
	   var x, y, numTypes, specsList, speakerVarsView;
	   
	   x = origin.x;
	   y = origin.y;
		
	   window		= SCWindow(name, Rect.new(x, y, 410+86, 528), false);
	   ~w = window;
	   window.view.decorator = FlowLayout(window.view.bounds);
	   specsList	= SCScrollView(window, Rect(0, 0, 160, 508))
				   .hasHorizontalScroller_(false)
				   .hasBorder_(true);
	   numTypes	= BMSpeakerSpec.specs.size;
	   specsList	= SCVLayoutView(specsList, Rect(4,4,150, numTypes * 24 + 4));
	   
	   BMSpeakerSpec.specs.keysDo({|spName|Ê
			SCDragSource(specsList, Rect(0, 0, 150, 20)).string_(" Ê " ++ spName.asString)
				.background_(Color.grey.alpha_(0.2))
				.font_(Font("Helvetica-Bold", 12))
				.beginDragAction_({ BMSpeaker(spec: spName) })
		});
	   
	   speakerListCompView	= SCCompositeView(window, 160 @ 508)
						   .background_(Color.grey.alpha_(0.3));
	   speakerListCompView.decorator = FlowLayout(speakerListCompView.bounds);
	   speakerList	= SCListView(speakerListCompView, 152 @ (508-35 ))
	 					.items_(outputArray.keys.asArray)
	 					.action_({| view | 
	 						var speaker	= outputArray[view.item];
		 					if ((outputArray.size > 0))
				    	  	    	   { if (speaker.isBMSpeaker)
			 					   { instanceVarsBoxes
			 						 .keysValuesDo{| key | 
			 						 	var value;
			 						 	value = speaker.perform(key);
			 						 	if (key == \index) { value = value + 1  };
			 						 	instanceVarsBoxes[key].value = value };
			 					   }
			 				   }
			 					 						       				   { instanceVarsBoxes
			 						 .keysValuesDo{| key | 
			 						 	instanceVarsBoxes[key].value = "" };
			 					   }
	 					});
	   
	
	 	speakerList.keyDownAction = { arg view,char,modifiers,unicode,keycode;
	 		block { |break|
				if((modifiers == 11534600) && (unicode == 63233), {
					outputArray.moveSpeakerDown(speakerList.value);
					break.value;
				});
				if((modifiers == 11534600) && (unicode == 63232), {
					upButton.doAction;
					break.value;
				});
				if(unicode == 127, { deleteButton.doAction });
				speakerList.defaultKeyDownAction(char,modifiers,unicode);
			}
		};

		speakerList.canReceiveDragHandler = { SCView.currentDrag.isKindOf(BMSpeaker) };
		speakerList.receiveDragHandler = { var newSpeaker = SCView.currentDrag; 
									  this.makeNewSpeakerWindow(newSpeaker)
									};



// List's Buttons ---------------------

		deleteButton				= RoundButton(speakerListCompView, 20 @ 20).extrude_(false).canFocus_(false);		deleteButton.states		= [[ '-', Color.black,  Color.white.alpha_(0.8) ]];
		deleteButton.action		= { var viewIndex;
							   	    if (speakerList.item.notNil)
							    	  	  { viewIndex = speakerList.value;
							    	  	    outputArray.removeAt(speakerList.item);
							    	  	    if ((viewIndex == (outputArray.size)) and: { outputArray.size > 0 })
							    	  	    	   { speakerList.valueAction = viewIndex - 1 }
							    	  	    	   { speakerList.value_(viewIndex).doAction }
							    	  	  }
							       };
	
		speakerListCompView.decorator.shift(6, 0);
		upButton					= RoundButton(speakerListCompView, 20 @ 20).extrude_(false).canFocus_(false);		upButton.states			= [[ \up, Color.black,  Color.white.alpha_(0.8) ]];
		upButton.action 			= { var index;
							    	    index 	= outputArray.keys.indexOf(speakerList.item);
								    if (index.notNil) { outputArray.moveSpeakerUp(index) }
							 	  };

		speakerListCompView.decorator.shift(-3, 0);
		
		downButton				= RoundButton(speakerListCompView, 20 @ 20).extrude_(false).canFocus_(false);		downButton.states 			= [[ \down, Color.black,  Color.white.alpha_(0.8) ]];
		downButton.action 			= { var index;
								    index 	= outputArray.keys.indexOf(speakerList.item);
								    if (index.notNil) { outputArray.moveSpeakerDown(index) }								  };
								  
		speakerListCompView.decorator.shift(6, 0); 				
		storeButton				= RoundButton(speakerListCompView, 46 @ 20).extrude_(false).canFocus_(false)
					 			  .font_(Font("Arial", 11)).states_([["Store", Color.black,  Color.white.alpha_(0.8) ]])
					 			  .action_{ outputArray.store };


		// instance variables
		speakerVarsView	= SCCompositeView(window, 160 @ 508).background_(Color.grey.alpha_(0.3));
		speakerVarsView.decorator = FlowLayout(speakerVarsView.bounds, Point(10, 10), Point(10, 10));
		
		instanceVarsBoxes	= [ \name -> "Name", \index -> "Hardware Out", \x -> "x", \y -> "y", \z -> "z" ]
						  .collectAs({| instVar |
						  	
						  	SCStaticText(speakerVarsView, 140 @ 25).string_(instVar.value ++ ":").font_(Font("Helvetica", 13));
						  	if (instVar.key == \name)
						  	 { instVar.value = SCTextField(speakerVarsView, 140 @ 25).boxColor_(Color.white.alpha_(0.3)) }						  	 { instVar.value = SCNumberBox(speakerVarsView, 140 @ 25).boxColor_(Color.white.alpha_(0.3)) };
						  	instVar.value.action_({| view | 
						  				 		var speaker = outputArray[speakerList.item], value = view.value;
						  				 		if (instVar.key == \index) { value = value - 1 };
						  				 		speaker.perform(instVar.key.asSetter, value)
						  					 });
						  	
						  	speakerVarsView.decorator.shift(0, 10);
						  	instVar
						   },
						   Event
						   );
		
		speakerList.doAction;
		speakerVarsView.decorator.shift(0, 41); 				
		SCStaticText.new(speakerVarsView, 140 @ 20).string = "Import / Export:";
		
		importPopUpMenu = SCPopUpMenu(speakerVarsView, 140 @ 20)
					   .items_([ " "/*, "Import Speaker Array", "Export Speaker Array" */])
					   .background_(Color.white)
					   .action_();
		
		window.onClose = { 
			outputArray.removeDependant(this); onClose.value(this)
		};
		
		window.front;
		
		
}	
		update {|tpv, what|
			
		 	speakerList.items_(outputArray.keys.asArray);
		 	speakerList.doAction;
		 	switch(what,
		 		\moveDown, { speakerList.value = speakerList.value + 1 },
		 		\moveUp, { speakerList.value = speakerList.value - 1 }
		 	)
		 }
		 
	makeNewSpeakerWindow {| newSpeaker, origin |

		var window, name, speakerNameField, okButton, speakerIndexField;
		 
		origin		= origin ?? { 490 @ 500 };
		window 		= SCWindow("New Speaker", Rect(origin.x, origin.y, 260, 110 + 30), false).userCanClose_(false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		SCStaticText(window, 50 @ 20).string = "Name:";

		speakerNameField	= SCTextView(window, 180 @ 20)
							.hasVerticalScroller_(false)
							.hasHorizontalScroller_(false)
							.enterInterpretsSelection_(false);
		
		SCStaticText(window, 50 @ 20).string = "HW Out:";
		speakerIndexField	= SCTextView(window, 180 @ 20)
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
				   			
				   			name = speakerNameField.string;
				   			if (name.size > 0) 
				   				{ name = name.asSymbol;
				   				  if (outputArray.keys.any{| nameInList | nameInList == name })
				   			        	{ BMAlert( "The name \"" ++ name ++ "\" is already taken. Please choose a different name.", 
				   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
				   			        			 background: Color.white,
				   			        			 color: Color.red,
				   			        			 border: false
				   			        	 ) 
				   			          }
				   			          { 
				   				
				   				  window.close;
				   				  newSpeaker.name = name;
				   				  newSpeaker.index = speakerIndexField.string.asInteger - 1;
								  outputArray.add(newSpeaker);
								  speakerList.value_(outputArray.lastIndex).doAction;
								  // don't forget to store the speakers

				   				  
				   				 }
				   				 
				   				 
				   				 }
				   				 

				   		   });
		speakerNameField.focus;
		window.front
	}		 
}
