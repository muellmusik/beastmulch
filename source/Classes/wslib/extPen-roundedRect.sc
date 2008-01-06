// wslib 2006

+ Pen {
	*roundedRect { |rect, radius| // radius can be array for 4 corners
	
		radius = radius ?? {  rect.width.min( rect.height ) / 2; };
		
		if( radius != 0 )
		{	radius = radius.asCollection.collect({ |item| 
				item ?? {  rect.width.min( rect.height ) / 2; }; });
			Pen.moveTo( rect.leftTop + ((radius@@0)@0) );
			Pen.addArc( rect.rightTop - ((radius@@1)@((radius@@1).neg)), (radius@@1), 
				-0.5pi, 0.5pi );
			Pen.addArc( rect.rightBottom - ((radius@@2)@(radius@@2)), (radius@@2),  0, 0.5pi );
			Pen.addArc( rect.leftBottom + ((radius@@3)@((radius@@3).neg)), (radius@@3),  
				0.5pi, 0.5pi );
			Pen.addArc( rect.leftTop + ((radius@@0)@(radius@@0)), (radius@@0),  pi, 0.5pi );
		}
		{	Pen.addRect( rect ); }
				
		}
		
	*extrudedRect {	 |rect, radius, width = 2, angle, inverse = false, colors|
	
		var centers;
		
		angle = angle ? 0.17pi;	
		radius = radius ? (rect.width.min(rect.height) * 0.5);
		
		radius = radius.asCollection;
		angle = angle.asCollection;
		
		colors = colors ? [ Color.white.alpha_(0.5), Color.black.alpha_(0.5) ];
		
		centers = [
			 (radius@@0)@(radius@@0),
			 ((radius@@1).neg)@(radius@@1),
			 ((radius@@2).neg)@((radius@@2).neg),
			 (radius@@3)@((radius@@3).neg)
			 ];
			
		centers = centers + [ rect.leftTop, rect.rightTop, rect.rightBottom, rect.leftBottom ];
			
			
		// light side
		 if( inverse ) { colors[1].set } { colors[0].set };
		
		 if( radius@@3 != 0 )
		 	{ Pen.moveTo( centers[3] + Polar( (radius@@3) - width,  pi - (angle@@1) ).asPoint );
		 	  Pen.addArc( centers[3], radius@@3, pi - (angle@@1), angle@@1 ); }
		 	{ Pen.moveTo( centers[3] + ((width)@(width.neg)));
		 		Pen.lineTo( rect.leftBottom ); };
		 
		 Pen.addArc( centers[0], radius@@0, pi, 0.5pi ); 
		 
		 if( radius@@1 != 0 )
		 	{	Pen.addArc( centers[1], radius@@1, 1.5pi,  0.5pi-(angle@@0) ); 
		 		Pen.addArc( centers[1], (radius@@1) - width, (angle@@0).neg, 
		 			(0.5pi-(angle@@0)).neg ); }
		 	{   Pen.lineTo( centers[1] ); Pen.lineTo( centers[1] + 
		 		((width.neg)@(width)) ); };
		 
		 Pen.addArc( centers[0], (radius@@0) - width, 1.5pi, -0.5pi );
		 
		 if( radius@@3 != 0 )
		 	{ Pen.addArc( centers[3], (radius@@3) - width, pi, (angle@@1).neg ); }
		 	{ Pen.lineTo( centers[3] + ((width)@(width.neg)) ) };
		 
		 Pen.fill; 
		 
		// dark side
		 if( inverse ) { colors[0].set } { colors[1].set };
		
		 if( radius@@1 != 0 )
		 	{ Pen.moveTo( centers[1] + Polar( (radius@@1) - width, (angle@@0).neg ).asPoint );
		 	  Pen.addArc( centers[1], radius@@1, (angle@@0).neg, (angle@@0) ); }
		 	{ Pen.moveTo( centers[1] + ((width)@(width.neg)) ); Pen.lineTo( centers[1] )  };
		 
		 Pen.addArc( centers[2], radius@@2, 0, 0.5pi ); 
		 
		 if( radius@@3 != 0 )
			{ Pen.addArc( centers[3], radius@@3, 0.5pi,  0.5pi-(angle@@1) ); 
		  	  Pen.addArc( centers[3], (radius@@3) - width, pi - (angle@@1), 
		  	  	(0.5pi-(angle@@1)).neg );  }
		  	{ Pen.lineTo( rect.leftBottom );
		  	  Pen.lineTo( centers[3] + ((width)@(width.neg))); };
		  	
		 Pen.addArc( centers[2], (radius@@2) - width, 0.5pi, -0.5pi );
		 
		 if( radius@@1 != 0 )
		 	{ Pen.addArc( centers[1], (radius@@1) - width, 0, (angle@@0).neg ); }
		 	{ Pen.lineTo( centers[1] + ((width.neg)@(width)) ) }; 
		 
		 Pen.fill; 
		 
	}
		
	*circle { |rect|
		var radius;
		radius = rect.width.min(rect.height) * 0.5;
		Pen.addArc( rect.center, radius, 0, 2pi );
		}
		
	*extrudedCircle { |rect, width = 2, angle, inverse = false, colors|
	
		var center, radius;
		
		angle = angle ? 0.17pi;	
		radius = rect.width.min(rect.height) * 0.5;
		
		colors = colors ? [ Color.white.alpha_(0.5), Color.black.alpha_(0.5) ];
		
		center = rect.center;
			
		// light side
		 if( inverse ) { colors[1].set } { colors[0].set };
		 
		 Pen.addAnnularWedge( center, radius - width, radius, pi - angle, pi );
		
		 Pen.fill; 
		 
		// dark side
		 if( inverse ) { colors[0].set } { colors[1].set };
		 
		 Pen.addAnnularWedge( center, radius - width, radius, angle.neg, pi );
		
		 Pen.fill; 
	}

	
}