/*
VBAP created by Ville Pukki
This version ported from PD code by Scott Wilson
Development funded by the AHRC
 
Copyright

This software is being provided to you, the licensee, by Ville Pulkki,
under the following license. By obtaining, using and/or copying this
software, you agree that you have read, understood, and will comply
with these terms and conditions: Permission to use, copy, modify and
distribute, including the right to grant others rights to distribute
at any tier, this software and its documentation for any purpose and
without fee or royalty is hereby granted, provided that you agree to
comply with the following copyright notice and statements, including
the disclaimer, and that the same appear on ALL copies of the software
and documentation, including modifications that you make for internal
use or for distribution:


 Written by Ville Pulkki 1999
 Helsinki University of Technology 
 and 
 Unversity of California at Berkeley
 
*/


VBAPSpeakerArray {
	classvar <>maxNumSpeakers = 55, minSideLength = 0.01;
	var <dim, speakers, <numSpeakers, <>sets;
	
//		typedef struct 		/* This defines the object as an entity made up of other things */
//	{
//		t_object x_ob;			/* gotta say this... it creates a reference to your object */
//		long x_ls_read;	 			/* 1 if loudspeaker directions have been read */
//		long x_triplets_specified;  /* 1 if loudspeaker triplets have been chosen */
//		t_ls x_ls[MAX_LS_AMOUNT];   /* loudspeakers */ // speakers
//		t_ls_set *x_ls_set;			/* loudspeaker sets */ // sets
//		void *x_outlet0;			/* outlet creation - inlets are automatic */
//		long x_ls_amount;			/* number of loudspeakers */ // numSpeakers
//		long x_dimension;		    /* 2 (horizontal arrays) or 3 (3d setups) */ // dim
//	} t_def_ls;

	// directions = [[azi, ele] * n]
	*newFromDirections { |dim, directions|
		^super.newCopyArgs(dim).initFromDirections(directions);
	}
	
	initFromDirections { |directions|
		if(dim == 2, {directions = directions.collect({|azi| [azi, 0]});});
		numSpeakers = directions.size;
		speakers = directions.collect({|dir| Speaker.newFromSpherical(dir[0], dir[1]) });
		this.anglesToCartesian;
	}
	
	anglesToCartesian {
		var atorad = 0.017453292519943295; // (2pi / 360)
		speakers.do({ |spkr|
			var azi, ele;
			azi = spkr.azimuth;
			ele = spkr.elevation;
			// [x, y, z]
			spkr.x = cos(azi * atorad) * cos(ele * atorad);
			spkr.y = sin(azi * atorad) * cos(ele * atorad);
			spkr.z = sin(ele * atorad);
		});
	}
	
	// i.e. 'BANG'
	getSetsAndMatrices {
		/* calculate and print out chosen loudspeaker sets and corresponding  matrices */

		if(dim == 3, {
			this.choose_ls_triplets;
			^this.calculate_3x3_matrixes;
		}, {
			if(dim == 2, {^this.choose_ls_tuplets});
		});
		postln("Error in loudspeaker direction data");
		^nil;
	}
	
	
	     /* Selects the loudspeaker triplets, and
      calculates the inversion matrices for each selected triplet.
     A line (connection) is drawn between each loudspeaker. The lines
     denote the sides of the triangles. The triangles should not be 
     intersecting. All crossing connections are searched and the 
     longer connection is erased. This yields non-intesecting triangles,
     which can be used in panning. 
     See theory in paper Pulkki, V. Lokki, T. "Creating Auditory Displays
     with Multiple Loudspeakers Using VBAP: A Case Study with
     DIVA Project" in International Conference on 
     Auditory Displays -98.*/
     
	chooseTriplets {
		//var i,j,k,l,m,li, table_size; //int
		var m, li, table_size;
		var i_ptr;	// int pte
		var vb1,vb2,tmp_vec; // instances of Speaker, orig t_ls
		var connections; // int connections[MAX_LS_AMOUNT][MAX_LS_AMOUNT];
		var angles; // float angles[MAX_LS_AMOUNT];
		var sorted_angles; //int sorted_angles[MAX_LS_AMOUNT];
		var distance_table; //float distance_table[((MAX_LS_AMOUNT * (MAX_LS_AMOUNT - 1)) / 2)];
		var distance_table_i; // int distance_table_i[((MAX_LS_AMOUNT * (MAX_LS_AMOUNT - 1)) / 2)];
		var distance_table_j;//int distance_table_j[((MAX_LS_AMOUNT * (MAX_LS_AMOUNT - 1)) / 2)];
		var distance; //float distance;
		var *trip_ptr, *prev, *tmp_ptr; //t_ls_set *trip_ptr, *prev, *tmp_ptr;

		//t_ls *lss = x->x_ls; this is now speakers
//if (ls_amount == 0) {
//post("define-loudspeakers: Number of loudspeakers is zero",0);
//    return;
//  }
		// init vars defined above
		connections = Array.fill(maxNumSpeakers, {Array.newClear(maxNumSpeakers)});
		angles = Array.newClear(maxNumSpeakers);
		sorted_angles = Array.newClear(maxNumSpeakers);
		distance_table = Array.newClear((maxNumSpeakers * (maxNumSpeakers - 1)) / 2);
		distance_table_i = Array.newClear((maxNumSpeakers * (maxNumSpeakers - 1)) / 2);
		distance_table_j = Array.newClear((maxNumSpeakers * (maxNumSpeakers - 1)) / 2);
  
		for(0, numSpeakers -1, {|i|
			for(i+1, numSpeakers -1, {|j|
				for(j+1 numSpeakers -1, {|k|
					if(vol_p_side_lgth(i,j,k) > minSideLength){
						connections[i][j]=1;
						connections[j][i]=1;
						connections[i][k]=1;
						connections[k][i]=1;
						connections[j][k]=1;
						connections[k][j]=1;
						//add_ldsp_triplet(i,j,k); //(i,j,k,x)
						sets = sets.add(VBAPSpeakerSet([i,j,k]));
					}
				});
			});
		});
   
		/*calculate distancies between all lss and sorting them*/
		table_size = ((numSpeakers - 1) * (numSpeakers)) / 2; 
		for(0, table_size -1, { |i| distance_table[i] = 100000.0 });
		for(0, numSpeakers - 1, { |i|
			for(i+1, numSpeakers - 1, {|j| 
				if(connections[i][j] == 1 {
					distance = abs(this.vec_angle(speakers[i],speakers[j]));
					k=0;
					while({distance_table[k] < distance}, {k = k+1});
					forBy(table_size - 1, k + 1, -1,{ |l|
						distance_table[l] = distance_table[l-1];
						distance_table_i[l] = distance_table_i[l-1];
						distance_table_j[l] = distance_table_j[l-1];
					});
					distance_table[k] = distance;
					distance_table_i[k] = i;
					distance_table_j[k] = j;
      			}, {table_size = table_size - 1;});
  			});
		});

	  /* disconnecting connections which are crossing shorter ones,
	     starting from shortest one and removing all that cross it,
	     and proceeding to next shortest */
		for(0, table_size - 1, {|i|
			var fst_ls, sec_ls;
			fst_ls = distance_table_i[i];
			sec_ls = distance_table_j[i];
			if(connections[fst_ls][sec_ls] == 1, {
				for(0, numSpeakers - 1, {|j|
					for(j+1, numSpeakers - 1, {|k|
						if( (j!=fst_ls) && (k != sec_ls) && (k!=fst_ls) && (j != sec_ls){
							if(lines_intersect(fst_ls, sec_ls, j,k), {
								connections[j][k] = 0;
								connections[k][j] = 0;
							});
						});
					});
				});
			});
		});

	  /* remove triangles which had crossing sides
	     with smaller triangles or include loudspeakers*/

		sets = sets.select({|set|
    			i = set.chanOffsets[0];
			j = set.chanOffsets[1];
			k = set.chanOffsets[2];
			(connections[i][j] == 0) || (connections[i][k] == 0) || (connections[j][k] == 0) 
				|| any_ls_inside_triplet(i,j,k);
		}):
	}
	
	//lines_intersect(int i,int j,int k,int l,t_ls  lss[MAX_LS_AMOUNT])
	lines_intersect { |i, j, k, l|
	     /* checks if two lines intersect on 3D sphere 
	       */
		var v1, v2, v3, neg_v3; // Speaker
		var angle;
		var dist_ij,dist_kl,dist_iv3,dist_jv3,dist_inv3,dist_jnv3; 
		var dist_kv3,dist_lv3,dist_knv3,dist_lnv3;
	
		v1 = unq_cross_prod(speakers[i], speakers[j]);
		v2 = unq_cross_prod(speakers[k], speakers[l]);
		v3 = unq_cross_prod(v1, v2);
		
		neg_v3 = Speaker.new;
		neg_v3.x= 0.0 - v3.x;
		neg_v3.y= 0.0 - v3.y;
		neg_v3.z= 0.0 - v3.z;
	
		dist_ij = (vec_angle(speakers[i], speakers[j]));
		dist_kl = (vec_angle(speakers[k], speakers[l]));
		dist_iv3 = (vec_angle(speakers[i], v3));
		dist_jv3 = (vec_angle(v3, speakers[j]));
		dist_inv3 = (vec_angle(speakers[i], neg_v3));
		dist_jnv3 = (vec_angle(neg_v3, speakers[j]));
		dist_kv3 = (vec_angle(speakers[k], v3));
		dist_lv3 = (vec_angle(v3, speakers[l]));
		dist_knv3 = (vec_angle(speakers[k], neg_v3));
		dist_lnv3 = (vec_angle(neg_v3, speakers[l]));
	
		/* if one of loudspeakers is close to crossing point, don't do anything*/
		if((abs(dist_iv3) <= 0.01) || (abs(dist_jv3) <= 0.01) || 
			(abs(dist_kv3) <= 0.01) || (abs(dist_lv3) <= 0.01) ||
			(abs(dist_inv3) <= 0.01) || (abs(dist_jnv3) <= 0.01) || 
			(abs(dist_knv3) <= 0.01) || (abs(dist_lnv3) <= 0.01), {^false});
	
		/* if crossing point is on line between both loudspeakers return 1 */
		if (((abs(dist_ij - (dist_iv3 + dist_jv3)) <= 0.01 ) &&
	       (abs(dist_kl - (dist_kv3 + dist_lv3))  <= 0.01)) ||
	      ((abs(dist_ij - (dist_inv3 + dist_jnv3)) <= 0.01)  &&
	       (abs(dist_kl - (dist_knv3 + dist_lnv3)) <= 0.01 )), { ^true}, {^false});

	}



  /* calculate volume of the parallelepiped defined by the loudspeaker
     direction vectors and divide it with total length of the triangle sides. 
     This is used when removing too narrow triangles. */
	vol_p_side_lgth { |i, j, k| // (int i, int j,int k, t_ls  lss[MAX_LS_AMOUNT] )
		var volper, lgth; // float
		var xprod; //t_ls xprod;
		
		xprod = this.unq_cross_prod(speakers[i], speakers[j]); // alters xprod
		volper = abs(this.vec_prod(xprod, speakers[k]));
		lgth = (abs(this.vec_angle(speakers[i], speakers[j])) 
          	+ abs(this.vec_angle(speakers[i], speakers[k])) 
          	+ abs(this.vec_angle(speakers[j], speakers[k])));
		if(lgth > 0.00001, { ^(volper / lgth)}, { ^0.0 });
	}
	
	//unq_cross_prod(t_ls v1,t_ls v2, t_ls *res) 
	/* vector cross product */
	unq_cross_prod { |v1, v2|
  		var length;
  		result.x = (v1.y * v2.z ) - (v1.z * v2.y);
  		result.y = (v1.z * v2.x ) - (v1.x * v2.z);
		result.z = (v1.x * v2.y ) - (v1.y * v2.x);
		length = this.vec_length(result);
		result.x = result.x / length;
		result.y = result.y / length;
		result.z = result.z / length;
		^result;
	}
	
	vec_length { |v1|
		/* length of a vector */
		^(sqrt(v1.x.squared + v1.y.squared + v1.z.squared));
	}
	
	vec_prod {|v1, v2|
		/* vector dot product */
		^((v1.x*v2.x) + (v1.y*v2.y) + (v1.z*v2.z));
	}
	
	vec_angle{ |v1, v2|
		/* angle between two loudspeakers */
  		var inner;
  		inner = ((v1.x*v2.x) + (v1.y*v2.y) + (v1.z*v2.z)) / 
  			(this.vec_length(v1) * this.vec_length(v2));
		if(inner > 1.0, {inner = 1.0});
		if (inner < -1.0, {inner = -1.0});
		^abs(acos(inner));
	}
	
	//any_ls_inside_triplet(int a, int b, int c,t_ls lss[MAX_LS_AMOUNT],int ls_amount)
	any_ls_inside_triplet { |a, b, c| // speakers, numSpeakers
   		/* returns true if there is loudspeaker(s) inside given ls triplet */
  		var invdetl //float invdet;
		var lp1, lp2, lp3; //Speakers // t_ls *lp1, *lp2, *lp3;
		var invmx; //float invmx[9];
		var i, j, k; //int i,j,k;
		var tmp; //float tmp;
		var any_ls_inside, this_inside; //int any_ls_inside, this_inside;

		lp1 =  speakers[a];
		lp2 =  speakers[b];
		lp3 =  speakers[c];
		
		inmx = Array.newClear(9);

		/* matrix inversion */
		invdet = 1.0 / (  lp1.x * ((lp2.y * lp3.z) - (lp2.z * lp3.y))
                    - (lp1.y * ((lp2.x * lp3.z) - (lp2.z * lp3.x)))
                    + (lp1.z * ((lp2.x * lp3.y) - (lp2.y * lp3.x))));

		invmx[0] = ((lp2.y * lp3.z) - (lp2.z * lp3.y)) * invdet;
		invmx[3] = ((lp1.y * lp3.z) - (lp1.z * lp3.y)) * invdet.neg;
		invmx[6] = ((lp1.y * lp2.z) - (lp1.z * lp2.y)) * invdet;
		invmx[1] = ((lp2.x * lp3.z) - (lp2.z * lp3.x)) * invdet.neg;
		invmx[4] = ((lp1.x * lp3.z) - (lp1.z * lp3.x)) * invdet;
		invmx[7] = ((lp1.x * lp2.z) - (lp1.z * lp2.x)) * invdet.neg;
		invmx[2] = ((lp2.x * lp3.y) - (lp2.y * lp3.x)) * invdet;
		invmx[5] = ((lp1.x * lp3.y) - (lp1.y * lp3.x)) * invdet.neg;
		invmx[8] = ((lp1.x * lp2.y) - (lp1.y * lp2.x)) * invdet;

		any_ls_inside = false;
		for(0, ls_amount - 1, {|i|
			if((i != a) && (i != b) && (i != c), {
				this_inside = true;
				for(0, 2, {|j|
					tmp = speakers[i].x * invmx[0 + (j*3)];
					tmp = speakers[i].y * invmx[1 + (j*3)] + tmp;
					tmp = speakers[i].z * invmx[2 + (j*3)] + tmp;
					if(tmp < -0.001, {this_inside = false;});
				});
      			if(this_inside, {any_ls_inside = true});
    			});
	  	});
	  ^any_ls_inside;
	}
	
//	add_ldsp_triplet{ |i, j, k| //(int i, int j, int k, t_def_ls *x) x = this
//     /* adds i,j,k triplet to structure*/
//
//	var trip_ptr, prev; //struct t_ls_set *trip_ptr, *prev;
//  trip_ptr = x->x_ls_set;
//  prev = NULL;
//  while (trip_ptr != NULL){
//    prev = trip_ptr;
//    trip_ptr = trip_ptr->next;
//  }
//  trip_ptr = (struct t_ls_set*) 
//    getbytes (sizeof (struct t_ls_set));
//  if(prev == NULL)
//    x->x_ls_set = trip_ptr;
//  else 
//    prev->next = trip_ptr;
//  trip_ptr->next = NULL;
//  trip_ptr->ls_nos[0] = i;
//  trip_ptr->ls_nos[1] = j;
//  trip_ptr->ls_nos[2] = k;
//  
//  sets = sets.add([i,j,k]);
//}

	calculate_3x3_matrixes {
     /* Calculates the inverse matrices for 3D */

		var invdet;
		var lp1, lp2, lp3; //t_ls *lp1, *lp2, *lp3;
		var invmx; //float *invmx;
		//float *ptr;
		//struct t_ls_set *tr_ptr = x->x_ls_set;
		var triplet_amount = 0, pointer,list_length=0; //int triplet_amount = 0, ftable_size,i,j,k, pointer,list_length=0;
		var at; //t_atom *at;
		//t_ls *lss = x->x_ls;
  
		if(sets.isNil, {
	    		postln("define-loudspeakers: Not valid 3-D configuration");
	    		^nil;
		});
	
 		triplet_amount = sets.size;
		list_length = triplet_amount * 21 + 2; /* was: + 3, pure data doesn't like errors on list_length */
		at = FloatArray.newClear(list_length);
  
		at[0] = dim;
		at[1] = numSpeakers;
		pointer=2;
  
		sets.do({|set|
    		lp1 = speakers[set.chanOffsets[0]];
			lp2 = speakers[set.chanOffsets[1]];
			lp3 = speakers[set.chanOffsets[2]];

			/* matrix inversion */
			invmx = set.inv_mx;
			invdet = 1.0 / (  (lp1.x * ((lp2.y * lp3.z)) - (lp2.z * lp3.y))
                    - (lp1.y * ((lp2.x * lp3.z)) - (lp2.z * lp3.x))
                    + (lp1.z * ((lp2.x * lp3.y)) - (lp2.y * lp3.x)));

			invmx[0] = ((lp2.y * lp3.z) - (lp2.z * lp3.y)) * invdet;
			invmx[3] = ((lp1.y * lp3.z) - (lp1.z * lp3.y)) * invdet.neg;
			invmx[6] = ((lp1.y * lp2.z) - (lp1.z * lp2.y)) * invdet;
			invmx[1] = ((lp2.x * lp3.z) - (lp2.z * lp3.x)) * invdet.neg;
			invmx[4] = ((lp1.x * lp3.z) - (lp1.z * lp3.x)) * invdet;
			invmx[7] = ((lp1.x * lp2.z) - (lp1.z * lp2.x)) * invdet.neg;
			invmx[2] = ((lp2.x * lp3.y) - (lp2.y * lp3.x)) * invdet;
			invmx[5] = ((lp1.x * lp3.y) - (lp1.y * lp3.x)) * invdet.neg;
			invmx[8] = ((lp1.x * lp2.y) - (lp1.y * lp2.x)) * invdet;
			3.do({|i|
				at[pointer] = set.chanOffsets[i] + 1;
				pointer = pointer + 1;
			});
			9.do({|i|
				at[pointer] = invmx[i];
				pointer = pointer + 1;
			});
			at[pointer] = lp1.x; pointer = pointer + 1;
			at[pointer] = lp2.x; pointer = pointer + 1;
			at[pointer] = lp3.x; pointer = pointer + 1;
			at[pointer] = lp1.y; pointer = pointer + 1;
			at[pointer] = lp2.y; pointer = pointer + 1;
			at[pointer] = lp3.y; pointer = pointer + 1;
			at[pointer] = lp1.z; pointer = pointer + 1;
			at[pointer] = lp2.z; pointer = pointer + 1;
			at[pointer] = lp3.z; pointer = pointer + 1;

		});
		// [dim, numSpeakers, [chanOffsets 0-2, invmx 0-8, [lp1, lp2, lp2].x, sim.y, sim.z] * sets.size].flat
		^at; 
	}
}

VBAPSpeakerSet { // triplet or pair
	var chanOffsets; //ls_nos[3];  /* channel numbers */
	var inv_mx; //float inv_mx[9]; /* inverse 3x3 or 2x2 matrix */
  //var next; //struct t_ls_set *next;  /* next set (triplet or pair) */
  
	*new {|chanOffsets|
  		^super.newCopyArgs(chanOffsets);
  	}
}	