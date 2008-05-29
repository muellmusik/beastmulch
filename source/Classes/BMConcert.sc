
BMConcert {	
	var speakers, controllers, <backupManager;
	var <concert;
	var savedConcert;
	

	*new {| speakers, controllers, backupManager | 
		  ^super.newCopyArgs(speakers, controllers, backupManager).init
	}

	init { 
		var pieces;
		
		if (backupManager.lastStoredSession.notNil) 
		   { pieces	= backupManager.lastStoredSession.concert.pieces ?? { List[] }}
		   { pieces	= List[] };
		concert	   	= (system: (speakers: speakers.deepCopy, controllers: controllers.deepCopy), pieces: pieces);
		CmdPeriod.add(this) 
	}
	
	add {| pieceEvent, indexInList |	 
		if (indexInList.notNil)
		   { concert.pieces.insert(indexInList + 1, pieceEvent) }
		   { concert.pieces.add(pieceEvent) };
		this.changed(\add, pieceEvent)
	}
	
	removeAt {| pieceEventIndex |
		concert.pieces.removeAt(pieceEventIndex);
		this.changed
	}
	
	loadAt  {| pieceEventIndex |
		this.changed(\loadPiece, concert.pieces[pieceEventIndex])
	}
}


BMConcertGUI  {

	var concertManager, configManager, name, window, windowView;
	var concertView, concertListView, listSection;
	var addButton, deleteButton, buttonSection, upButton, downButton, importSection, exportButton, importButton, storeButton;
	var loadButton, configButton, systemSetup, configText;
	var >onClose;
	var selectable, pieceLoaded = false, loadButtonStates;
	
	*new {| concertManager, configManager, name, origin |
		  ^super.newCopyArgs(concertManager, configManager, name).init.makeWindow(origin ? (40@200));
	}
	
	init {
		concertManager.addDependant(this);
		configManager.addDependant(this)
	}
	
	makeWindow {| origin |
	
		var x, y;
		x = origin.x;
		y = origin.y;
		
		window 					= SCWindow(name, Rect.new(x, y, 410, 456), false).userCanClose = false;
		windowView				= SCCompositeView(window, Rect(5, 5, 400, 435))
								  .background_(Color.white.alpha_(0.2));
		
		concertView 				= SCCompositeView(windowView, Rect(0, 0, 200, 425));
		concertView.decorator 		= FlowLayout(concertView.bounds, Point(10, 10), Point(10, 10));
		SCStaticText.new(concertView, 180 @ 20).string = "Pieces";


		// Pieces List ---------------------
	
		concertListView				= SCListView(concertView, 180 @ 373);
		concertListView.keyDownAction 	= { arg view,char,modifiers,unicode,keycode;
								    
									    if(unicode == 127, { deleteButton.action.value(0) });
									    if (unicode == 16rF700, { concertListView.valueAction = concertListView.value - 1 });
									    if (unicode == 16rF703, { concertListView.valueAction = concertListView.value + 1 });
									    if (unicode == 16rF701, { concertListView.valueAction = concertListView.value + 1 });
									    if (unicode == 16rF702, { concertListView.valueAction = concertListView.value - 1 })								    
									  };
		
		concertListView.action			= {| view | 
									   postf("from the Concert Editor, listview action, the value of the view is %\n", view.value);
									   
									   pieceLoaded = false; 
								   	   
								   	   if ((concertManager.concert.pieces.size > 0))
									      { configText.string = concertManager.concert.pieces[concertListView.value].config;
									        loadButton.states = loadButtonStates.loadSelected } 
									      { configText.string = "";
									        loadButton.states = loadButtonStates.noPieces
							    	  	    	  }
								  	  };
								  						concertListView.mouseDownAction  = {| view |
								  													   postf("from mouse action, the value of the view is %\n", view.value);
								  	   if (selectable.not) 
								  		  { this.listViewSelection(selectable = true);
								  		    if (concertManager.concert.pieces.size > 0)
								  		    	  { configText.string = concertManager.concert.pieces[concertListView.value].config } 
								  		    	  { configText.string = "" }
								  		  }
								  		  
								  	   };
								  	   
		concertListView.background_(Color.white).hiliteColor_(Color.new255(51, 111, 203, 255 * 0.95));
		concertView.decorator.shift(0, -6);
		
		
		// List's Buttons ---------------------
		
		addButton					= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		addButton.states 			= [[ '+', Color.black, Color.white.alpha_(0.8) ]];
		addButton.action 			= { this.makeSelectConfigurationWindow((), 490 @ 500) };
							  	  
		concertView.decorator.shift(-8, 0);
		
		deleteButton				= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		deleteButton.states		= [[ '-', Color.black,  Color.white.alpha_(0.8) ]];
		deleteButton.action		= { var viewIndex;
							   	    if (concertListView.item.notNil)
							    	  	  { viewIndex = concertListView.value;
							    	  	    concertManager.removeAt(viewIndex);
							    	  	    if ((viewIndex == (concertManager.concert.pieces.size)) and: { concertManager.concert.pieces.size > 0 })
							    	  	    	   { concertListView.valueAction = viewIndex - 1 }
							    	  	    	   { concertListView.action.value(viewIndex) }
							    	  	  }
							       };

		
		concertView.decorator.shift(4, 0);
		upButton					= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		upButton.states			= [[ \up, Color.black,  Color.white.alpha_(0.8) ]];
		upButton.action 			= { var index;
							    
								    index 	= concertManager.concert.pieces.collect{|x| x.name }.indexOf(concertListView.item);
								    if (index.notNil and: {index > 0 })
								    	   { concertManager.concert.pieces = concertManager.concert.pieces.swap(index - 1, index);
								    	     concertManager.changed;
								    	     concertListView.valueAction = index - 1
								    	   }
							 	  };

		concertView.decorator.shift(-8, 0);
		
		downButton				= RoundButton(concertView, 20 @ 20).extrude_(false).canFocus_(false);		downButton.states 			= [[ \down, Color.black,  Color.white.alpha_(0.8) ]];
		downButton.action 			= { var index;
			
								    index 	= concertManager.concert.pieces.collect{|x| x.name }.indexOf(concertListView.item);
								    if (index.notNil and: { index < (concertManager.concert.pieces.size - 1) })
								    	  { concertManager.concert.pieces = concertManager.concert.pieces.swap(index, index + 1);
								    	    concertManager.changed;
								    	    concertListView.valueAction = index + 1
								    	  }
								  };
								  
		concertView.decorator.shift(4, 0); 				
		storeButton				= RoundButton(concertView, 46 @ 20).extrude_(false).canFocus_(false)
					 			  .font_(Font("Arial", 11)).states_([["Store", Color.black,  Color.white.alpha_(0.8) ]])
					 			  .action_{| view | concertManager.backupManager.makeSessionBackup(concertManager, configManager) };

								  
		// Second Column -------------
						  
		buttonSection 			= SCCompositeView(windowView, Rect(200, 10, 200 + 56, 425));
		buttonSection.decorator 	= FlowLayout(buttonSection.bounds, Point(10, 10), Point(10, 10));
		buttonSection.decorator.shift(0, 20);
				
		loadButton				= RoundButton(buttonSection, 180 @ 20).extrude_(false).canFocus_(false);		loadButtonStates			= (noPieces: 		[[ "No Pieces Available", Color.black, Color.white.alpha_(0.8) ]],
		 						   noSelection:	[[ "No Piece Selected", Color.black, Color.white.alpha_(0.8) ]],
		 						   loadSelected: 	[[ "Load Selected Piece", Color.black, Color.white.alpha_(0.8) ]],
		 						   pieceLoaded: 	[[ "Piece Loaded", Color.black, Color.green.alpha_(0.2) ]]
		 						  );
								   
		loadButton.states 			= if (concertManager.concert.pieces.size > 0)
									{ loadButtonStates.loadSelected }
									{ loadButtonStates.noPieces };
				
		loadButton.action 			= {| view |  
								   if (selectable)
								   	 { concertManager.loadAt(concertListView.value);
								   	   view.states = loadButtonStates.pieceLoaded;
								   	   pieceLoaded = true
								   	 }
								  };
		
		buttonSection.decorator.shift(0, 10);
		
		SCStaticText.new(buttonSection, 180 @ 20).string = "Piece Configuration:";
		configText	= SCStaticText.new(buttonSection, 180 @ 20).background_(Color.white)
					  .align_(\center).stringColor_(Color.new255(51, 111, 203, 255 * 0.95))
					  .font_(Font("Helvetica-Bold", 14));
		buttonSection.decorator.shift(0, 135 + 56);	
			
		SCStaticText.new(buttonSection, 180 @ 20).string = "Import / Export:";
		
		importButton 			= RoundButton(buttonSection, 180 @ 20).extrude_(false).canFocus_(false);
		importButton.states 	= [[ "Import Concert", Color.black, Color.white.alpha_(0.8)  ]];
		importButton.action 	= {CocoaDialog.getPaths({| path | 
								var recalled;
								
								recalled			= Object.readTextArchive(path[0]);
								concertManager.concert.pieces = recalled.concert.pieces.deepCopy;
								configManager.currentConfig_('all off', \concertEditor);
								configManager.clear;
								
								if (recalled.configurations.names.indexOf('all off').notNil)
									{ recalled.configurations.dict.removeAt('all off');
									  recalled.configurations.names.removeAt(recalled.configurations.names.indexOf('all off'))
									};
								
								recalled.configurations.names
									.do{| name | 
										configManager.dict.add(name -> recalled.configurations.dict[name])
									   };
								configManager.names = configManager.names ++ recalled.configurations.names;
								if (selectable.not) { this.listViewSelection(selectable = true) };
								concertListView.action.value(0);
								concertManager.backupManager.makeSessionBackup(concertManager, configManager);
								}, maxSize: 1)
							   };	
		
		exportButton 			= RoundButton(buttonSection,180 @ 20).extrude_(false).canFocus_(false);
		exportButton.states 	= [[ "Export Concert", Color.black, Color.white.alpha_(0.8)  ],[ "Export Concert", Color.black, Color.white.alpha_(0.8) ]];
		exportButton.action 	= { CocoaDialog.savePanel({| path | 
									this.prepareForExport.writeTextArchive(path);
									concertManager.backupManager.makeSessionBackup(concertManager, configManager) 
							     })
							  };

	    this.update; 
	    this.listViewSelection(selectable = false);
	    
		window.onClose 			= { concertManager.removeDependant(this); 
								    onClose.value(this) 
								  };
		window.front
	}
			
	update {| changed, change, config, from |
		    "concert window' update function called".postln;
		    if ((change == \currentConfig) and: { from == \configurationEditor }) 
		    	  { 	if (selectable) { this.listViewSelection(selectable = false) } }
			  {  postf("Pieces in concert: %\n", concertManager.concert.pieces);
			  	concertListView.items 	= concertManager.concert.pieces.collect{| x | x.name }.asArray
			  }
		
	}
		
	prepareForExport {	var names, dict;
									
					names	= List[];
					dict		= ();
					concertManager.concert.pieces
						.do{| piece | 
						    var configName = piece.config;
						    
						    if (names.indexOf(configName).isNil) 
						    	  { dict.add(configName -> configManager.dict[configName]);
						    	    names.add(configName)
						    	  }
						};
					^(concert: concertManager.concert.deepCopy, configurations: (dict: dict.deepCopy, names: names))
	}
	
	
	makeSelectConfigurationWindow {| event, origin |
		
		var window, name, configsView, okButton;
		 
		window 		= SCWindow("Select a Configuration", Rect(origin.x, origin.y, 200 + 20 , 317 + 60), false).userCanClose_(false);
		
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		configsView 				= SCListView(window, 200 @ 317).canReceiveDragHandler = false;
		configsView.background_(Color.white).hiliteColor_(Color.new255(51, 111, 203, 255 * 0.95)); 
		configsView.items 			= configManager.names.asArray;
		
		RoundButton(window, 95 @ 20)
			   .extrude_(false).canFocus_(false)
			   .states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
			   .action_({	window.close });
			   
		okButton = RoundButton(window, 95 @ 20)
				   .extrude_(false).canFocus_(false)
				   .states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
				   .action_({ window.close;
				   			event.add('config' -> configsView.item);
				   			this.makeSelectSourceWindow(event, 490 @ 500)
				   		   });
				   		   
		
		window.front
	}
	
	makeSelectSourceWindow {| event, origin |
		
		var window, name, button, okButton;
		 
		window 		= SCWindow("Sources", Rect(origin.x, origin.y, 260, 110), false).userCanClose_(false);
		
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		 
		button 		= RoundButton(window, 240 @ 20)
						.extrude_(false).canFocus_(false)
				 		.states_([[ "Do you want to use a soundfile? Yes", Color.black, Color.green(alpha: 0.2) ],
				 				 [ "Do you want to use a soundfile? No", Color.black, Color.clear ]
				 				]);

		window.view.decorator.shift(0, 30);
		
		RoundButton(window, 115 @ 20)
			   .extrude_(false).canFocus_(false)
			   .states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
			   .action_({	window.close });
			   
		okButton = RoundButton(window, 115 @ 20)
				   .extrude_(false).canFocus_(false)
				   .states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
				   .action_({ var origin =  490 @ 500;
				   
				   			window.close;
				   			if (button.value == 0)
				   			   { CocoaDialog.getPaths({| path | 
				   			   					   event.add(\path -> path[0]);
												   this.makeNewPieceWindow(event, origin);
											       }, 
											       maxSize:1
								)
							   }
							   { this.makeNewPieceWindow(event, origin) }
				   		   });
		window.front
	}
	
	makeNewPieceWindow {| event, origin |

		var window, name, pieceNameField, okButton, suggestedName;
		 
		suggestedName		= if (event[\path].notNil)	{ event[\path].basename.splitext[0] } { "Fileless Piece" };
									
		window 			= SCWindow("Select a Name", Rect(origin.x, origin.y, 260, 110), false).userCanClose_(false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		SCStaticText(window, 50 @ 20).string = "Name:";

		pieceNameField	= SCTextView(window, 180 @ 20)
							.string_(suggestedName)
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
				   .states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
				   .action_({ var name;
				   			
				   			name = pieceNameField.string;
				   			if (name.size > 0) 
				   			   { name = name.asSymbol;
				   			     if (concertManager.concert.pieces.any{| e | e.name == name })
				   			        	{ BMAlert( "The name \"" ++ name ++ "\" is already taken. Please choose a different name.", 
				   			        			 [[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]],
				   			        			 background: Color.white,
				   			        			 color: Color.red,
				   			        			 border:false
				   			        	 ) 
				   			          }
					   				{ window.close;
					   				  event.add(\name -> name);
					   				  concertManager.add(event, concertListView.value);
					   				  if (selectable.not) { this.listViewSelection(selectable = true) };
					   				  concertListView.valueAction = concertListView.value + 1
					   				}
					   		   }
				   		   });
		pieceNameField.focus;
		window.front
	}

	listViewSelection {| condition |
					
					if (condition)
					   { concertListView.selectedStringColor = Color.white;
						concertListView.hiliteColor = Color.new255(51, 111, 203, 255 * 0.95);
						loadButton.states 	= loadButtonStates.loadSelected;
						pieceLoaded 		= false
					   }
					   { concertListView.selectedStringColor = Color.black;
					   	concertListView.hiliteColor = Color.clear; 
					   	configText.string = "";
					   	loadButton.states = if (concertManager.concert.pieces.size > 0)
											{ loadButtonStates.noSelection }
											{ loadButtonStates.noPieces };
					   	pieceLoaded 		= false
					   }
	}
}