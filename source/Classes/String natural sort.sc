+ String {
	
	
	///* These are defined as macros to make it easier to adapt this code to
	// * different characters types or comparison functions. */
	//static inline int
	//nat_isdigit(nat_char a)
	//{
	//     return isdigit((unsigned char) a);
	//}
	
	// Char:isDecDigit
	
	
	// isSpace
	//static inline int
	//nat_isspace(nat_char a)
	//{
	//     return isspace((unsigned char) a);
	//}
	
	
	//toUpper
	//static inline nat_char
	//nat_toupper(nat_char a)
	//{
	//     return toupper((unsigned char) a);
	//}
	//
	
	
	//static int
	//compare_right(nat_char const *a, nat_char const *b)
	compareRight{ |string|
		var bias = 0, a, b;
	     
	     /* The longest run of digits wins.  That aside, the greatest
		value wins, but we can't know that it will until we've scanned
		both numbers to know that they have the same magnitude, so we
		remember it in BIAS. */
	    // for (;; a++, b++) {
	    inf.do({|i|
			a = this[i];
			b = string[i];
		    if ((a.isNil or: {a.isDecDigit.not})  &&  (b.isNil or: {b.isDecDigit.not}), {^bias});
		    if (a.isNil or: {a.isDecDigit.not}, {^-1});
		    if (b.isNil or: {b.isDecDigit.not}, {^1});
			if (a < b, {
			       if (bias == 0, { bias = -1 });
			}, { 
				if (a > b, { 
					if (bias == 0, { bias = 1 },{
						if (a.isNil  &&  b.isNil, {^bias});
					});
				});
			});
		});
		^0;
	}
	
	
	//static int
	//compare_left(nat_char const *a, nat_char const *b)
	compareLeft { |string|
		var a, b;
	     /* Compare two left-aligned numbers: the first to have a
	        different value wins. */
		inf.do({|i|
			a = this[i];
			b = string[i];
		  	if((a.isNil or: {a.isDecDigit.not})  &&  (b.isNil or: {b.isDecDigit.not}), {^0});
			if(a.isNil or: {a.isDecDigit.not}, {^-1});
	
			if(b.isNil or: {b.isDecDigit.not}, {^1});
			
			if(a < b, {^-1});
			
			if(a > b, {^1});
	
	     });
		  
	     ^0;
	}
	
	
	//static int strnatcmp0(nat_char const *a, nat_char const *b, int fold_case)
	naturalCompare { |string, ignoreCase = false|
	     var ai, bi; // int
	     var ca, cb; // nat_char
	     var fractional, result; // int
	     
	     //assert(a && b);
	     ai = bi = 0;
	     while(true, {
		  ca = this[ai]; cb = string[bi];
	
		  /* skip over leading spaces or zeros */
		  while ({ca.notNil and: {ca.isSpace}}, {ca = this[ai = ai + 1]});
		  
		  while ({cb.notNil and: {cb.isSpace}}, {cb = string[bi = bi + 1]});
			
		if (ca.isNil && cb.isNil, {^0});
		       /* The strings compare the same.  Perhaps the caller
	                  will want to call strcmp to break the tie. */
	
		  /* process run of digits */
		  if (ca.notNil && cb.notNil and: {ca.isDecDigit  &&  cb.isDecDigit}, {
		       fractional = (ca == $0 || cb == $0);
	
		       if (fractional, {
				if((result = this.copyToEnd(ai).compareLeft(string.copyToEnd(bi))) != 0, {
					^result
				});
		       }, {
		       	if((result = this.copyToEnd(ai).compareRight(string.copyToEnd(bi))) != 0, {
		       		^result
		       	});
		       });
		  });
	
		// moved up
	//	  if (!ca && !cb) {
	//	       /* The strings compare the same.  Perhaps the caller
	//                  will want to call strcmp to break the tie. */
	//	       return 0;
	//	  }
	
		  if (ignoreCase, {
		       ca = ca.toUpper;
		       cb = cb.toUpper;
		  });
		  
		  if (ca < cb, {^-1});
		  if (ca > cb, {^1});
	
		  ai = ai + 1; bi = bi + 1;
	     });
	}
	
	
	//
	//int strnatcmp(nat_char const *a, nat_char const *b) {
	//     return strnatcmp0(a, b, 0);
	//}
	//
	//
	///* Compare, recognizing numeric string and ignoring case. */
	//int strnatcasecmp(nat_char const *a, nat_char const *b) {
	//     return strnatcmp0(a, b, 1);
	//}


}
