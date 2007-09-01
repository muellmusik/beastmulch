ConcertConfig {
	var <list;

	*new {
	
		  ^super.new.init;
	
	}

	init { 
		   
		   this.clear;
		   CmdPeriod.add(this) 
		   
	}
		  
	clear{ 
		   
		   list = List[] 
		   
	}
	
	list_{| x |
	 
		 list = x;
		 this.changed
		 
	}
 
	add {| piece, indexInList |
		 
		if (list.includes(piece).not)
		   { if (indexInList.notNil)
		   	   { list.insert(indexInList + 1, piece) }
		   	   { list.add(piece) };
		   	   
		   	this.changed(\add, piece)
		   }
		   { warn(piece ++ " was already added to the Concert Configuration") }
		   
	}
	
	remove {| piece |
		    
		    list.remove(piece);
		    this.changed
		    
	}
	
	load	  {| piece |
		   
		   this.changed(\load, piece)
	
	}
	
}

ConcertConfigGUI {

	var concertConfig, presetManager, systemSetup, name, window;
	var presetsSection, concertSection, presetView, concertView, listSection;
	var addButton, deleteButton, buttonSection, upButton, downButton, importSection, exportButton, importButton;
	var loadButton, configButton;
	var >onClose;
	
	
	*new {| concertConfig, presetManager, systemSetup, name, origin |
	
		  ^super.newCopyArgs(concertConfig, presetManager, systemSetup, name).init.makeWindow(origin ? (40@200));
	
	}
	
	init {
	
		concertConfig.addDependant(this);
		presetManager.addDependant(this)
		
	}
	
	makeWindow {| origin |
	
		var x, y;
		x = origin.x;
		y = origin.y;
		
		window 					= SCWindow(name, Rect.new(x, y, 220, 375), false);
		window.view.decorator 		= FlowLayout(Rect.new(0, 0, 645, 375), Point(10, 10), Point(10, 10));
		
		concertSection 			= SCVLayoutView(window, Rect(0,0,200,375));
		SCStaticText.new(concertSection, Rect(0,0,180,20))//.font_(Font("Crush49", 14))
				   .string = "Presets in Concert Configuration";

		concertView				= SCListView(concertSection, Rect(0, 0, 200, 250)).canReceiveDragHandler = true;
		concertView.receiveDragHandler = { concertConfig.add(SCView.currentDrag) };
		
		concertView.keyDownAction 	= { arg view,char,modifiers,unicode,keycode;
								    
								    if(unicode == 127, { concertConfig.remove(view.item) });
								    if (unicode == 16rF700, { concertView.valueAction = concertView.value - 1 });
								    if (unicode == 16rF703, { concertView.valueAction = concertView.value + 1 });
								    if (unicode == 16rF701, { concertView.valueAction = concertView.value + 1 });
								    if (unicode == 16rF702, { concertView.valueAction = concertView.value - 1 })								    
								  };
		concertView.background 		= HiliteGradient(Color.blue.alpha_(0.5), Color.green.alpha_(0.3), steps: 256);
		
		SCStaticText.new(concertSection, Rect(0,0,80,0)).string_(" ");// placeholder
		
		loadButton				= SCButton(concertSection, Rect(0,0,200,20)).canReceiveDragHandler = false;		loadButton.states 			= [[ "Load Selected Preset", Color.black,Color.clear]];
		loadButton.action 			= { concertConfig.load(concertView.items[concertView.value].asSymbol) };
							  	  		
		SCStaticText.new(concertSection, Rect(0,0,80,10)).string_(" ");// placeholder
			
		configButton				= SCButton(concertSection, Rect(0,0,200,20)).canReceiveDragHandler = false;		configButton.states		= [["Edit Concert Configuration", Color.black,Color.clear], ["Hide Details", Color.black,Color.clear]];
		configButton.action		= {| value |
								   var temp;
	
								   if ((value.value == 1))
								   	  { [ buttonSection, presetsSection ].do(_.visible = true);
								   	    temp	= window.bounds.width = 640;
								   	    window.bounds = temp }
								   	  { [ buttonSection, presetsSection ].do(_.visible = false);
								   
								   	    temp	= window.bounds.width = 220;
								   	    window.bounds = temp
								   	  }
								  };


		buttonSection 			= SCVLayoutView(window,Rect(0,0,200,360)).visible = false;
		SCStaticText.new(buttonSection, Rect(0,0,80,20)).string_(" ");// placeholder
		
		addButton					= SCButton(buttonSection, Rect(0,0,200,20)).canReceiveDragHandler = false;		addButton.states 			= [[ "Add to Concert", Color.black,Color.clear]];
		addButton.action 			= { if (presetView.item.notNil) 
									   { concertConfig.add(presetView.item, concertView.value);
									     concertView.valueAction = concertView.value + 1
									   }
							  	  };
							  	  
		
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder

		deleteButton				= SCButton(buttonSection, Rect(0,0,200,20)).canReceiveDragHandler = false;		deleteButton.states		= [["Delete from Concert", Color.black,Color.clear]];
		deleteButton.action		= { var viewIndex;
							   	    if (concertView.item.notNil)
							    	  	  { viewIndex = concertView.value;
							    	  	    concertConfig.remove(concertView.item);
							    	  	    if ((viewIndex == (concertConfig.list.size)) and: { concertConfig.list.size > 0 })
							    	  	    	   { concertView.valueAction = viewIndex - 1 }
							    	  	  }
							       };

		SCStaticText.new(buttonSection, Rect(0,0,80,10)).string_(" ");// placeholder
		
		SCStaticText.new(buttonSection, Rect(0,0,180,20)).string_("Move");
		upButton					= SCButton(buttonSection, Rect(0,0,200,20)).canReceiveDragHandler = false;		upButton.states			= [[ "Up", Color.black,Color.clear]];
		upButton.action 			= { var index;
							    
								    index 	= concertConfig.list.indexOf(concertView.item);
								    if (index.notNil and: {index > 0 })
								    	   { concertConfig.list = concertConfig.list.swap(index - 1, index);
							    	          concertView.valueAction = index - 1
								    	   }
							 	  };

		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
			
		downButton				= SCButton(buttonSection, Rect(0,0,200,20)).canReceiveDragHandler = false;		downButton.states 			= [[ "Down", Color.black,Color.clear]];
		downButton.action 			= { var index;
			
								    index 	= concertConfig.list.indexOf(concertView.item);
								    if (index.notNil and: { index < (concertConfig.list.size - 1) })
								    	  { concertConfig.list = concertConfig.list.swap(index, index + 1);
								    	    concertView.valueAction = index + 1
								    	  }
								  };
			
		SCStaticText.new(buttonSection, Rect(0,0,80,10)).string_(" ");// placeholder
		SCStaticText.new(buttonSection, Rect(0,0,180,20)).string = "Import / Export";
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		
		exportButton 			= SCButton.new(buttonSection, Rect(0,0,110,20));
		exportButton.states 	= [[ "Export Concert Configuration", Color.black, Color.new255(72, 61, 139).alpha_(0.6) ]];
		exportButton.background	= Color.clear;
		exportButton.action 	= { CocoaDialog.savePanel({| path | 
									
									var presetNames, presetDict, concertPresetM;
									
									presetNames	= concertConfig.list.collect(_.asString);
									
									presetDict	= IdentityDictionary[];
									presetNames.do{| presetN | 
												 presetManager.presetName = presetN;
												 presetDict.add(presetN.asSymbol -> presetManager.get(presetManager.lastID).deepCopy);
												 };
									IdentityDictionary[ \systemSetup -> systemSetup,
												      \presetNames -> presetNames, 
												      \presetDict -> presetDict
												    ]
										.writeTextArchive(path);
									
									concertPresetM= PresetManager(\concert, path.basename.splitext[0]);
									concertPresetM.add(presetDict.deepCopy);
									
							    
							    })
							  };
		
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		
		importButton 			= SCButton.new(buttonSection, Rect(0,0,110,20));
		importButton.states 	= [[ "Import Concert Configuration", Color.black, Color.clear ]];
		importButton.action 	= { CocoaDialog.getPaths({| path | 
								 var recalled, presetNames, presetDict, systemSetup, concertPresetM;
								
								 recalled 		= Object.readTextArchive(path[0]);
								 concertPresetM	= PresetManager(\concert, path[0].basename.splitext[0]);
								 concertPresetM.add(presetDict.deepCopy);
								 
								 systemSetup		= recalled[\systemSetup];
								 presetNames 		= recalled[\presetNames];
								 presetDict 		= recalled[\presetDict];
								 
								 presetDict.keysValuesDo
								 	{| key, value |
								 	  presetManager.presetName = key.asString;
								 	  presetManager.add(value.deepCopy)
								 	 };
								 
								 concertConfig.clear;
								 presetNames.do{| name | concertConfig.add(name.asSymbol) };
							    }, maxSize:1)
							   };	

		SCStaticText.new(buttonSection, Rect(0,0,80,0)).string_(" ");// placeholder
		SCStaticText.new(buttonSection, Rect(0,0,180,45)).string_("Cmd-drag or use button to add, select and press delete to remove.");
		//.align_(\center);///.font_(Font("CoffeeCup", 14));
		
		
		presetsSection			= SCVLayoutView(window, Rect(0,0,200,360)).visible = false;
		SCStaticText(presetsSection, Rect(0,0,180,20)).string_("Stored Piece Presets");//.font_(Font("Crush49", 14))
		
		presetView 				= SCListView(presetsSection, Rect(0, 0, 200, 317)).canReceiveDragHandler = false;
		presetView.beginDragAction 	= {|view| view.item };
		presetView.background 		= HiliteGradient(Color.blue.alpha_(0.3), Color.green.alpha_(0.3), steps: 317);
		
			

	    this.update; 
		window.onClose 			= { concertConfig.removeDependant(this); 
							   	    presetManager.removeDependant(this); 
								    onClose.value(this) 
								  };
		window.front
	}
	
			
	update {
		concertView.items 	= concertConfig.list.asArray;
		presetView.items 	= presetManager.storedPresets.difference(concertView.items)
	}
}