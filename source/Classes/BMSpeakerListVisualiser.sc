BMSpeakerListVisualiser : BMAbstractGUI {

	var speakerList, radius = 12, lowerel = 0;
	var maxXinv, lowX, hiX, hiY, lowY, hiZ, lowZ, zoom, labels, qcView;
	var colours, floorZ, viewSpeakers;
	
	*new {|speakerList| ^super.new.init(speakerList).makeWindow }
	
	init {|argspeakerList|
		speakerList = argspeakerList;
	}
	
	//speakerList_ {|list| }
	
	showBoids_{|bool| qcView.showBoids = bool }
	
	boidPositions_{|boidsArray| qcView.positions = boidsArray }
	
	makeWindow {
		var rect, subArraysCols, speakerColours;
	//
	//	//speakerList = [[-22.5, -35, 1.2022282867427], [22.5, 10, 1], [-67.5, 10, 1], [67.5, -35, 1.2022282867427], [-112.5, -35, 1.2022282867427], [112.5, 10, 1], [-157.5, 10, 1], [157.5, -35, 1.2022282867427]].collectAs({|coords, i| i->BMSpeaker.newFromSpherical(i.asString, indices[i], coords[0], coords[1], coords[2], '8030A')}, BMInOutArray);
	//
	//	speakerList = [ -22.5, 22.5, -67.5, 67.5, -112.5, 112.5, -157.5, 157.5 ].collectAs({|azi, i| i->BMSpeaker.newFromSpherical(i.asString, indices[i], azi, lowerel, radius, 'SCM50')}, BMInOutArray);
	//	
	//	speakerList.do({|assoc| assoc.value.y = assoc.value.y * 1.5 });
	//	
	//	// el of 5m for upper assuming lower is 1m
	//	speakerList.copy.do({|assoc, i| speakerList.add((i+8)->assoc.value.deepCopy.index_(indices[i]+8).z_(5));});
	//	
	//	(8..13).do({|i| var speak; speak = speakerList.at(i).value; speak.y = speak.y + 4 });
		
		
		#lowX, lowY, lowZ, hiX, hiY, hiZ = speakerList.boundaries.flat;
		
		maxXinv = [lowX, hiX].abs.maxItem.reciprocal;
		#hiY, lowY, hiZ, lowZ = [hiY, lowY, hiZ, lowZ] * maxXinv;
		
		
		
		//maxX = speakerList.collectAs({|spkr| spkr.x.abs }, Array).maxItem;
//		yvals = speakerList.collectAs({|spkr| spkr.y }, Array);
//		hiY = yvals.maxItem / maxX;
//		lowY = yvals.minItem / maxX;
//		
//		
//		hiZ = speakerList.collectAs({|spkr| spkr.z }, Array).maxItem / maxX;
//		lowZ = speakerList.collectAs({|spkr| spkr.z }, Array).minItem / maxX;
		
		window = SCWindow("Speakers", rect = Rect(100,200, 1020, 1000 / ([hiY, lowY].abs.maxItem) * 1.2)).front;
		//SCButton(w, Rect(0, 0, 150, 20))
		//	.states_([["pick another QC file"]])
		//	.action_({ File.openDialog("", { |path| m.path_(path) }) });
		zoom = SCSlider(window, Rect(0,0,rect.width, 20));
//		labels = RoundButton(window, Rect(rect.width - 20, 0, 20, 20))
//			.extrude_(false)
//			.canFocus_(false)
//			.states_([[ "Labels Off"], ["Labels On"]] )
//			.action_({qcView.labels = labels.value.booleanValue});
		qcView = SCQuartzComposerView(window, rect.moveTo(0,20));
		qcView.path = this.class.filenameSymbol.asString.dirname ++ "/QC/SpeakerVis.qtz";
		qcView.showBoids = true;
		qcView.resize_(5);
		
		//qcView.maxFPS_(20);
		zoom.action = {qcView.zoom = zoom.value * 4 - 2;};
		
		zoom.doAction;
		qcView.sphereScale = 0.02;
		//qcView.labels_(false);
		
		colours = Pseq([Color.green.alpha_(0.95), Color.red.alpha_(0.95), Color.blue.alpha_(0.95), Color.yellow.alpha_(0.95), Color.white.alpha_(0.95)], inf).asStream;
		//colours = Pseq([Color.green, Color.red, Color.blue, Color.yellow, Color.white], inf).asStream;
		subArraysCols = speakerList.subArrays.collectAs({|key| key->colours.next }, IdentityDictionary) ?? { () };
		speakerColours = ();
		subArraysCols.keysValuesDo({|key, value| 
			speakerList.getSubArrayKeys(key).do({|speakKey| speakerColours[speakKey] = value;});
		});
		
		qcView.startY = hiY;
		qcView.endY = lowY;
//		floorZ = speakerList.collectAs({|spkr| spkr.z }, Array).minItem / maxX;
//		qcView.floorZ = floorZ - 0.06;
		
		floorZ = -0.8  * maxXinv - 0.09;
		qcView.floorZ = floorZ;
		
		viewSpeakers = speakerList.associationsCollectAs({|assoc| 
			var x, y, z, colour, tilt;
			x = assoc.value.x  * maxXinv;
			y = assoc.value.y  * maxXinv;
			z = assoc.value.z  * maxXinv;
			colour = speakerColours[assoc.key] ?? {colours.next};
			tilt = atan2(z, hypot(x, y)) * 57.295779513082;
			[x, y, z, colour, Point(x, y).theta * 57.295779513082, assoc.key.asString, cos(assoc.value.azi * 0.017453292519943) * tilt, sin(assoc.value.azi * 0.017453292519943).neg * tilt] 
			
		}, Array);
	//	
	//	k = KDTree(speakerList.collectAs({|assoc| 
	//		var x, y, z;
	//		x = assoc.value.x / maxX;
	//		y = assoc.value.y / maxX;
	//		z = assoc.value.z / maxX;
	//		
	//		[x, y, z, assoc.value.index] 
	//		
	//	}, Array), lastIsLabel: true);
	//	
	//	
	//	
		//k.nearest([0, 0, 0])[0].label
		
		//
		//
		//p = Polar(0.5, 0);
		//r = { p = p.rotate(0.25pi); [p.asPoint.x, p.asPoint.y, 0.1, Color.new255(139, 123, 139).alpha_(0.6), p.theta * 57.295779513082, "8030"]} ! 8;
		//
		//p = p.rotate(0.125pi);
		//
		//p = p.scale(0.8);
		//r = r ++ ({ p = p.rotate(0.25pi); [p.asPoint.x, p.asPoint.y, 0.2, Color.green.alpha_(0.6), p.theta * 57.295779513082, "8040"]} ! 8);
		//
		//p = p.scale(2);
		//
		//r = r ++ ({ p = p.rotate(0.25pi); [p.asPoint.x, p.asPoint.y, 0.15, Color.red.alpha_(0.6), p.theta * 57.295779513082, "ATC"]} ! 8);
		
		//m.showSpeakers = false;
		qcView.speakers = viewSpeakers;
		qcView.start;
		// dim = 3, numBoids, centre, limits, velMax, velScale, minDist
		//b = BMGrainBoidSpace(3, numGrains, nil, [[-1.2, lowY * 1.2, lowZ * 1.5], [1.2, hiY * 1.2, hiZ * 1.5]], 0.2, 1, 0.1);
	//	
	//	treeRout = Routine({
	//		loop({
	//			k.nearest(b.moveNext.pos)[0].label.yield
	//		})
	//	});
	//	
	//	draw = Task({
	//	AppClock.sched(0, {
	//	//b.numBoids.do({b.moveNext});
	//	qcView.positions = b.boids.collect(_.pos);
	//	0.05
	//	});
	//	});
	//	draw.start;
	//	window.onClose = { draw.stop };
	}
}