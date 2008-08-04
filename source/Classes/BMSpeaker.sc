BMSpeaker {
	classvar rad2deg;
	var <name; // matches speaker taxonomy we've hashed out
	
	var <index; // SC output
	
	// cartesian
	var <>x, <>y, <>z; // in meters; for 2D arrays z = 0;
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
	
	name_ {|newname| name = newname.asSymbol; } // setter necessary?
	
	asUGenInput { ^index }
	
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
	var concertView, speakersListView, listSection;
	var addButton, deleteButton, buttonSection, upButton, downButton, storeButton;
	var loadButton, configButton, systemSetup, configText;
	var >onClose;
	var selectable, pieceLoaded = false, loadButtonStates, importPopUpMenu;
	
	var speakerListView;
	
	*new {| outputArray, name, origin |
		  ^super.newCopyArgs(outputArray, name).init.makeWindow(origin ? (40@200));
	}
	
	init {
		outputArray.addDependant(this);
//		configManager.addDependant(this)
	}
	
	makeWindow {| origin |
	
		var x, y, numTypes, specsList;
		x = origin.x;
		y = origin.y;
		
		window 		= SCWindow(name, Rect.new(x, y, 410, 508 + 20), false);
		window.view.decorator = FlowLayout(window.view.bounds);
		specsList		= SCScrollView(window, Rect(0, 0, 160, 508))
				   .hasHorizontalScroller_(false)
				   .hasBorder_(true);
	   numTypes	     = BMSpeakerSpec.specs.size;
	   specsList     = SCVLayoutView(specsList, Rect(4,4,150, numTypes * 24 + 4));
	   
	   BMSpeakerSpec.specs.keysDo({|spName|Ê
			SCDragSource(specsList, Rect(0, 0, 150, 20)).string_(" Ê " ++ spName.asString)
				.background_(Color.grey.alpha_(0.2))
				.font_(Font("Helvetica-Bold", 12))
				.beginDragAction_({ BMSpeaker("sergio", spec: spName) })
		});
	   
	   speakerListView = SCListView(window, 150 @ 508)
	 		.items_(outputArray.keys);
//	 	listView.enterKeyAction = {
//	 		var plgin;
//	 		plgin = trimPluginsStrip.plugins[listView.value];
//	 		plgin.notNil.if({plgin.gui}); 
//	 	}; // can duplicate
//	 	speakerListView.keyDownAction = { arg view,char,modifiers,unicode,keycode;
//	 		block { |break|
//				if((modifiers == 11534600) && (unicode == 63233), {
//					trimPluginsStrip.movePluginDown(listView.value);
//					break.value;
//				});
//				if((modifiers == 11534600) && (unicode == 63232), {
//					trimPluginsStrip.movePluginUp(listView.value);
//					break.value;
//				});
//				if(unicode == 127, {trimPluginsStrip.removePlugin(listView.value)});
//				listView.defaultKeyDownAction(char,modifiers,unicode);
//			}
//		};
//		listView.mouseDownAction = {|view, x, y, modifiers, buttonNumber, clickCount|
//			if(clickCount == 2, {
//				listView.enterKeyAction.value;
//			});
//		};
		speakerListView.canReceiveDragHandler = { SCView.currentDrag.isKindOf(BMSpeaker) };
		speakerListView.receiveDragHandler = { outputArray.add(SCView.currentDrag) };
//		speakerListView.beginDragAction = {/* trimPluginsStrip.plugins[listView.value].copy */};
		window.front
}	
		update {|tpv, what|
		 	//if(what == \trim, {ezKnob.value = trimPluginsStrip.trim;});
		 	speakerListView.items_(outputArray.keys.asArray.postln);
//		 	switch(what,
//		 		\moveDown, {listView.value = listView.value + 1},
//		 		\moveUp, {listView.value = listView.value - 1}
//		 	)
		 }
		 
		 
		//concertView 				= SCCompositeView(windowView, Rect(0, 0, 200, 425));
//		concertView.decorator 		= FlowLayout(concertView.bounds, Point(10, 10), Point(10, 10));
//		SCStaticText.new(concertView, 180 @ 20).string = "Speaker Specs";
//
//
//		// Pieces List ---------------------
//	
//		speakersListView				= SCListView(concertView, 180 @ 373);
//		speakersListView.keyDownAction 	= { arg view,char,modifiers,unicode,keycode;
//								    
//									    if(unicode == 127, { deleteButton.action.value(0) });
//									    if (unicode == 16rF700, { speakersListView.valueAction = speakersListView.value - 1 });
//									    if (unicode == 16rF703, { speakersListView.valueAction = speakersListView.value + 1 });
//									    if (unicode == 16rF701, { speakersListView.valueAction = speakersListView.value + 1 });
//									    if (unicode == 16rF702, { speakersListView.valueAction = speakersListView.value - 1 })								    
//									  };
//		
//		speakersListView.action			= {| view | 
//									   postf("from the Concert Editor, listview action, the value of the view is %\n", view.value);
//									   
//									   pieceLoaded = false; 
//								   	   
//								   	   if ((concertManager.concert.pieces.size > 0))
//									      { configText.string = concertManager.concert.pieces[speakersListView.value].config;
//									        loadButton.states = loadButtonStates.loadSelected } 
//									      { configText.string = "";
//									        loadButton.states = loadButtonStates.noPieces
//							    	  	    	  }
//								  	  };
//								  						speakersListView.mouseDownAction  = {| view |
//								  													   postf("from mouse action, the value of the view is %\n", view.value);
//								  	   if (selectable.not) 
//								  		  { this.listViewSelection(selectable = true);
//								  		    if (concertManager.concert.pieces.size > 0)
//								  		    	  { configText.string = concertManager.concert.pieces[speakersListView.value].config } 
//								  		    	  { configText.string = "" }
//								  		  }
//								  		  
//								  	   };
//								  	   
//		speakersListView.background_(Color.white).hiliteColor_(Color.new255(51, 111, 203, 255 * 0.95));
//		concertView.decorator.shift(0, -6);
//		
//		
//		// List's Buttons ---------------------
//		
//		addButton					= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		addButton.states 			= [[ '+', Color.black, Color.white.alpha_(0.8) ]];
//		addButton.action 			= { this.makeSelectConfigurationWindow(
//										{| configsViewItem |
//										  this.makeSelectSourceWindow((config: configsViewItem))
//										}
//									)
//								   };
//							  	  
//		concertView.decorator.shift(-8, 0);
//		
//		deleteButton				= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		deleteButton.states		= [[ '-', Color.black,  Color.white.alpha_(0.8) ]];
//		deleteButton.action		= { var viewIndex;
//							   	    if (speakersListView.item.notNil)
//							    	  	  { viewIndex = speakersListView.value;
//							    	  	    concertManager.removeAt(viewIndex);
//							    	  	    if ((viewIndex == (concertManager.concert.pieces.size)) and: { concertManager.concert.pieces.size > 0 })
//							    	  	    	   { speakersListView.valueAction = viewIndex - 1 }
//							    	  	    	   { speakersListView.action.value(viewIndex) }
//							    	  	  }
//							       };
//
//		
//		concertView.decorator.shift(4, 0);
//		upButton					= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		upButton.states			= [[ \up, Color.black,  Color.white.alpha_(0.8) ]];
//		upButton.action 			= { var index;
//							    
//								    index 	= concertManager.concert.pieces.collect{|x| x.name }.indexOf(speakersListView.item);
//								    if (index.notNil and: {index > 0 })
//								    	   { concertManager.concert.pieces = concertManager.concert.pieces.swap(index - 1, index);
//								    	     concertManager.changed;
//								    	     speakersListView.valueAction = index - 1
//								    	   }
//							 	  };
//
//		concertView.decorator.shift(-8, 0);
//		
//		downButton				= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		downButton.states 			= [[ \down, Color.black,  Color.white.alpha_(0.8) ]];
//		downButton.action 			= { var index;
//			
//								    index 	= concertManager.concert.pieces.collect{|x| x.name }.indexOf(speakersListView.item);
//								    if (index.notNil and: { index < (concertManager.concert.pieces.size - 1) })
//								    	  { concertManager.concert.pieces = concertManager.concert.pieces.swap(index, index + 1);
//								    	    concertManager.changed;
//								    	    speakersListView.valueAction = index + 1
//								    	  }
//								  };
//								  
//		concertView.decorator.shift(4, 0); 				
		//storeButton				= RoundButton(concertView, 46 @ 20).extrude_(false).canFocus_(false)
//					 			  .font_(Font("Arial", 11)).states_([["Store", Color.black,  Color.white.alpha_(0.8) ]])
//					 			  .action_{| view | concertManager.backupManager.makeSessionBackup(concertManager, configManager) };
//
//								  
//		// Second Column -------------
//						  
//		buttonSection 			= SCCompositeView(windowView, Rect(200, 10, 200 + 56, 425));
//		buttonSection.decorator 	= FlowLayout(buttonSection.bounds, Point(10, 10), Point(10, 10));
//		buttonSection.decorator.shift(0, 20);
//				
//		loadButton				= RoundButton(buttonSection, 180 @ 20).extrude_(false).canFocus_(false);		loadButtonStates			= (noPieces: 		[[ "No Pieces Available", Color.black, Color.white.alpha_(0.8) ]],
//		 						   noSelection:	[[ "No Piece Selected", Color.black, Color.white.alpha_(0.8) ]],
//		 						   loadSelected: 	[[ "Load Selected Piece", Color.black, Color.white.alpha_(0.8) ]],
//		 						   pieceLoaded: 	[[ "Piece Loaded", Color.black, Color.green.alpha_(0.2) ]]
//		 						  );
//								   
//		loadButton.states 			= if (concertManager.concert.pieces.size > 0)
//									{ loadButtonStates.loadSelected }
//									{ loadButtonStates.noPieces };
//				
//		loadButton.action 			= {| view |  
//								   if (selectable)
//								   	 { if (pieceLoaded.not)
//								   	 	  { concertManager.loadAt(speakersListView.value);
//								   	 	    view.states = loadButtonStates.pieceLoaded;
//								   	 	    pieceLoaded = true
//								   	 	  }
//								   	 	  { ("The Piece \"" ++ speakersListView.item ++ "\" has already been loaded").inform }
//								   	 }
//								  };
//		
//		buttonSection.decorator.shift(0, 10);
//		
//		SCStaticText.new(buttonSection, 180 @ 20).string = "Piece Configuration:";
//		configText	= SCStaticText.new(buttonSection, 180 @ 20).background_(Color.white)
//					  .align_(\center).stringColor_(Color.new255(51, 111, 203, 255 * 0.95))
//					  .font_(Font("Helvetica-Bold", 14));
//		buttonSection.decorator.shift(0, 247);	
//			
//		SCStaticText.new(buttonSection, 180 @ 20).string = "Import / Export:";
//		
//		importPopUpMenu = SCPopUpMenu(buttonSection, 180 @ 20)
//					   .items_([ " ",
//					   		    "Import Concert", "Export Concert", "-",
//					   		    "Import Piece", "Export Piece", "-",
//					   		    "Import Configuration", "Export Configuration"   
//					   		  ])
//					   .background_(Color.white)
//					   .action_({| view |
//					   	  switch(view.value,
//					   	   	1,
//					   	   	{ CocoaDialog.getPaths({| path | 
//								var recalled;
//								
//								recalled = Object.readTextArchive(path[0]);
//								concertManager.concert.pieces = recalled.concert.pieces.deepCopy;
//								configManager.currentConfig_('all off', \concertEditor);
//								configManager.clear;
//								
//								if (recalled.configurations.names.indexOf('all off').notNil)
//									{ recalled.configurations.dict.removeAt('all off');
//									  recalled.configurations.names.removeAt(recalled.configurations.names.indexOf('all off'))
//									};
//								
//								recalled.configurations.names
//									.do{| name | 
//										configManager.dict.add(name -> recalled.configurations.dict[name])
//									   };
//								configManager.names = configManager.names ++ recalled.configurations.names;
//								if (selectable.not) { this.listViewSelection(selectable = true) };
//								speakersListView.action.value(0);
//								concertManager.backupManager.makeSessionBackup(concertManager, configManager);
//								}, maxSize: 1);
//
//							 },
//							 
//							 2,
//							 {  CocoaDialog.savePanel({| path | 
//							 	this.prepareForExport.writeTextArchive(path);
//							 	concertManager.backupManager.makeSessionBackup(concertManager, configManager) 
//							    })
//					  		 },
//					  		  
//					  		 4,
//					  		 { CocoaDialog.getPaths({| path | 
//								var piece, configuration, pieceAndConfig, completion;
//								
//								pieceAndConfig = Object.readTextArchive(path[0]);
//								piece = pieceAndConfig.piece;
//								configuration = pieceAndConfig.config;
//								completion = { concertManager.add(piece, speakersListView.value);
//										      pieceAndConfig = (piece: piece, config: configuration);
//										      configManager.backupManager.makeSessionBackup(concertManager, configManager)
//					   	  				         .add(\piece, piece.name, pieceAndConfig);
//					   	  				      if (selectable.not) { this.listViewSelection(selectable = true) };
//					   	  				      speakersListView.valueAction = speakersListView.value + 1
//										    };
//										    
//								this.makeNewNameWindow(
//									piece.name, 
//									concertManager.concert.pieces.collect{| e | e.name },
//									{| newName | 
//					  				piece.name = newName;
//					  				if (configManager.names.any{| e | e == piece.config })
//					  				 { if (configuration.value != configManager.dict[piece.config])
//					  				   	    
//					  					{ BMAlert("The name of the Configuration used by this Piece is already in use by a different Configuration.", 
//			   			        			    [[ "Rename it", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ],
//			   			        			     [ "Use current", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]
//			   			        			    ],
//			   			        			    [ { this.makeNewNameWindow(
//			   			        			          piece.config, 
//			   			        			          configManager.names, 
//			   			        			          {| newName | 
//			   			        			           piece.config = newName;
//			   			        			           configuration = newName -> configuration.value;
//			   			        			           configManager.add(configuration);
//			   			        			           completion.value;
//			   			        			           configManager.backupManager.add(\configuration, configuration.key, configuration)
//			   			        			           }
//										        )
//										      },
//										      { configuration.value = configManager.dict[piece.config];
//										        completion.value 
//										      }
//			   			        			    ],
//			   			        			    background: Color.white, color: Color.red, border:false
//			   			        			  );
//			   			        			  
//					  					}
//					  					{ completion.value };
//				   					}
//				   					{ configManager.add(configuration);
//				   					  completion.value
//				   					} 
//				   				}
//								)
//							 }, maxSize: 1)
//							 },
//					  		 
//					  		 5, 
//					  		 { if (selectable and: { speakersListView.items.size > 0 }) 
//					  		 	  {  CocoaDialog.savePanel({| path | 
//					  		 	  		var piece, configuration, pieceAndConfig;
//					  		 	  		
//					  		 	  		piece = concertManager.concert.pieces[speakersListView.value].deepCopy;
//					  		 	  		configuration = piece.config -> configManager.dict[piece.config].deepCopy;
//					  		 	  		pieceAndConfig = (piece: piece, config: configuration);
//					  		 	  		pieceAndConfig.writeTextArchive(path);
//					  		 	  		configManager.backupManager.makeSessionBackup(concertManager, configManager)
//										             .add(\piece, piece.name, pieceAndConfig)
//										             .add(\configuration, configuration.key, configuration)
//									})
//								  }
//					  		 	  { BMAlert( "Please select a Piece", 
//		   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
//		   			        			 background: Color.white, color: Color.red, border:false
//		   			        	 		) 
//		   			          	  }
//					  		 },
//							 
//							 7, 
//					  		 { CocoaDialog.getPaths({| path | 
//								var configuration;
//								
//								configuration = Object.readTextArchive(path[0]);
//								this.makeNewNameWindow(
//									configuration.key, 
//									configManager.names, 
//									{| newName | 
//									  configuration = newName -> configuration.value;
//									  configManager.add(configuration);
//									  configManager.backupManager.makeSessionBackup(concertManager, configManager)
//							 	       	          .add(\configuration, configuration.key, configuration)
//							 	     }
//								)
//							 }, maxSize: 1)
//							 },
//							 
//							 8,
//							 { this.makeSelectConfigurationWindow(
//							   	{| configName | 
//							   	 var configuration;
//							   	
//							   	 configuration = configName -> configManager.dict[configName].deepCopy;
//							   	 CocoaDialog.savePanel({| path | 
//							   	 	configuration.writeTextArchive(path);
//							   	 	configManager.backupManager.makeSessionBackup(concertManager, configManager)
//							 			.add(\configuration, configuration.key, configuration)
//							 	 })
//					  		     }
//					  		   )
//					  		 }
//					  	);
//					  	
//					  	view.value = 0
//					  });
//
//	    this.update; 
//	    
////		window.onClose 			= { concertManager.removeDependant(this); 
////								    onClose.value(this) 
////								  };
//		window.front
//	}
			
//	update {| changed, change, config, from |
//		    speakersListView.items 	= BMSpeakerSpec.specs.keys.asArray	}
//		
//	prepareForExport {	var names, dict;
//									
//					names	= List[];
//					dict		= ();
//					concertManager.concert.pieces
//						.do{| piece | 
//						    var configName = piece.config;
//						    
//						    if (names.indexOf(configName).isNil) 
//						    	  { dict.add(configName -> configManager.dict[configName]);
//						    	    names.add(configName)
//						    	  }
//						};
//					^(concert: concertManager.concert.deepCopy, configurations: (dict: dict.deepCopy, names: names))
//	}
//	
//	
//	makeSelectConfigurationWindow {| action, origin |
//		
//		var window, name, configsView, okButton;
//		 
//		origin		= origin ?? { 490 @ 500 };
//		window 		= SCWindow("Select a Configuration", Rect(origin.x, origin.y, 200 + 20 , 317 + 60), false).userCanClose_(false);
//		
//		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
//		
//		configsView 				= SCListView(window, 200 @ 317).canReceiveDragHandler = false;
//		configsView.background_(Color.white).hiliteColor_(Color.new255(51, 111, 203, 255 * 0.95)); 
//		configsView.items 			= configManager.names.asArray;
//		
//		RoundButton(window, 95 @ 20)
//			   .extrude_(false).canFocus_(false)
//			   .states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
//			   .action_({	window.close });
//			   
//		okButton = RoundButton(window, 95 @ 20)
//				   .extrude_(false).canFocus_(false)
//				   .states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
//				   .action_({ window.close;
//				   			action.value(configsView.item)
//				   			
//				   		   });
//				   		   
//		
//		window.front
//	}
//	
//	makeSelectSourceWindow {| event, origin |
//		
//		var window, name, button, okButton;
//		 
//		origin		= origin ?? { 490 @ 500 };
//		window 		= SCWindow("Sources", Rect(origin.x, origin.y, 260, 110), false).userCanClose_(false);
//		
//		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
//		 
//		button 		= RoundButton(window, 240 @ 20)
//						.extrude_(false).canFocus_(false)
//				 		.states_([[ "Do you want to use a soundfile? Yes", Color.black, Color.green(alpha: 0.2) ],
//				 				 [ "Do you want to use a soundfile? No", Color.black, Color.clear ]
//				 				]);
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
//				   .states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
//				   .action_({ var origin =  490 @ 500;
//				   
//				   			window.close;
//				   			if (button.value == 0)
//				   			   { CocoaDialog.getPaths({| path | 
//				   			   					   event.add(\path -> path[0]);
//												   this.makeNewPieceWindow(event, origin);
//											       }, 
//											       maxSize:1
//								)
//							   }
//							   { this.makeNewPieceWindow(event, origin) }
//				   		   });
//		window.front
//	}
//	
//	makeNewPieceWindow {| event, origin |
//
//		var suggestedName;
//		 
//		suggestedName		= if (event[\path].notNil)	{ event[\path].basename.splitext[0] } { "Fileless Piece" };
//		this.makeNewNameWindow(suggestedName,
//							concertManager.concert.pieces.collect{| e | e.name },
//							{| newName | 
//							  event.add(\name -> newName);
//						   	  concertManager.add(event, speakersListView.value);
//						   	  concertManager.backupManager.makeSessionBackup(concertManager, configManager);
//						   	  if (selectable.not) { this.listViewSelection(selectable = true) };
//						   	  speakersListView.valueAction = speakersListView.value + 1
//						   	}
//		)							
//		
//	}
//	
//	
//	makeNewNameWindow {| suggestedName, usedNames, action, origin |
//		var window, pieceNameField, okButton;
//		 
//		origin		= origin ?? { 490 @ 500 };
//		window 		= SCWindow("Select a Name", Rect(origin.x, origin.y, 260, 110), false).userCanClose_(false);
//		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
//		
//		SCStaticText(window, 50 @ 20).string = "Name:";
//
//		pieceNameField	= SCTextView(window, 180 @ 20)
//							.string_(suggestedName.asString)
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
//				   .states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
//				   .action_({ var name;
//				   			
//				   			name = pieceNameField.string;
//				   			if (name.size > 0) 
//				   			   { name = name.asSymbol;
//				   			   
//				   			     if (usedNames.any{| e | e == name })
//				   			        	{ BMAlert( "The name \"" ++ name ++ "\" is already taken. Please choose a different name.", 
//				   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
//				   			        			 background: Color.white,
//				   			        			 color: Color.red,
//				   			        			 border:false
//				   			        	 ) 
//				   			          }
//					   				{ window.close;
//					   				  action.value(name)
//					   				}
//					   		   }
//				   		   });
//		pieceNameField.focus;
//		window.front
//	}
//
//
//	listViewSelection {| condition |
//					
//					if (condition)
//					   { speakersListView.selectedStringColor = Color.white;
//						speakersListView.hiliteColor = Color.new255(51, 111, 203, 255 * 0.95);
//						loadButton.states 	= loadButtonStates.loadSelected;
//						pieceLoaded 		= false
//					   }
//					   { speakersListView.selectedStringColor = Color.black;
//					   	speakersListView.hiliteColor = Color.clear; 
//					   	configText.string = "";
//					   	loadButton.states = if (concertManager.concert.pieces.size > 0)
//											{ loadButtonStates.noSelection }
//											{ loadButtonStates.noPieces };
//					   	pieceLoaded 		= false
//					   }
//	}
}
