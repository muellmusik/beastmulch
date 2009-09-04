/*
 *  fastInverseSqrt.h
 *  xSC3plugins
 *
 *  Created by Scott Wilson on 15/08/2005.
 *  Copyright 2005 Scott Wilson. All rights reserved.
 *
 */

#if defined( __GNUC__ )
#include<ppc_intrinsics.h>
#endif
#include <math.h>
#include <vecLib/vDSP.h>
#include <stdint.h>

//inline int checkBadValues(float samp) {
//	int classification = fpclassify(samp);
//	switch (classification) 
//	{ 
//		case FP_INFINITE: 
//			printf("Infinite number found\n"); 
//			break; 
//		case FP_NAN: 
//			printf("NaN found\n"); 
//			//			 break; 
//			//		 default: 
//			//			 printf("Normal %f %i\n", samp, classification); 
//	};
//	return classification;
//}

#if defined(__ppc__) || defined(__ppc64__)

inline float FastScalarInvSqrt( float f ) 
{
	
	float estimate, estimate2;
	float oneHalf = 0.5f;
	float one = oneHalf + oneHalf;
	
	//if(finite(f) == 0 ) printf("f is not finite\n");
	
	//Calculate a 5 bit starting estimate for the reciprocal sqrt
	estimate = estimate2 = __frsqrte ( f );
	
	//if(finite(estimate) == 0 ) printf("initial estimate is not finite\n");
	
	//if you require less precision, you may reduce the number of loop iterations
	estimate = estimate + oneHalf * estimate * ( one - f * estimate * estimate );
	estimate = estimate + oneHalf * estimate * ( one - f * estimate * estimate );
	estimate = estimate + oneHalf * estimate * ( one - f * estimate * estimate );
	
	//if(finite(estimate) == 0 ) printf("final estimate is not finite\n");
	
	return __fsels( -f, estimate2, estimate );
	
}

//#else
//
//inline float FastScalarInvSqrt( float x ) 
//{
//	//printf("Intel\n");
//	float xhalf = 0.5f * x;
//	int i = *(int*)&x; // store floating-point bits in integer
//	i = 0x5f3759d5 - (i >> 1); // initial guess for Newton's method
//	x = *(float*)&i; // convert new bits into float
//	x = x*(1.5f - xhalf*x*x); // Three rounds of Newton's method
//	x = x*(1.5f - xhalf*x*x);
//	x = x*(1.5f - xhalf*x*x);
//	return x;
//	
//}

#endif // __ppc__ || __ppc64__

// safe for zero and negative numbers, but not for Nan
inline float FastScalarSqrt( float f ) {
	//checkBadValues(f);
	float returnval;
// fast version for PPC	
#if defined(__ppc__) || defined(__ppc64__)
	returnval = f == 0.f ? 0.f :  f * (f < 0.f ? FastScalarInvSqrt(-f) : FastScalarInvSqrt(f));
	//checkBadValues(returnval);
#else
	returnval = f == 0.f ? 0.f : sqrtf(fabsf(f)); // fast enough on intel, and faster than anything else...
	//returnval = f == 0.f ? 0.f :  f * (f < 0.f ? FastScalarInvSqrt(-f) : FastScalarInvSqrt(f));
#endif // __ppc__ || __ppc64__
	
	return returnval;
}

// Vector Version
#if __VEC__
inline vector float vecReciprocalSquareRoot( vector float v )
{
	//Get the square root reciprocal estimate
	vector float zero = (vector float)(0);
	vector float oneHalf = (vector float)(0.5);
	vector float one = (vector float)(1.0);
	//vector float absv = vec_abs(v); // safe for negative values
	vector float estimate = vec_rsqrte( v );
	
	// assume inf was 0 and correct using one's complment of compare mask
	estimate = vec_andc(estimate, vec_cmpeq(estimate, (vector float)(INFINITY)));
	
	//3 rounds of Newton-Raphson refinement
	vector float estimateSquared = vec_madd( estimate, estimate, zero );
	vector float halfEstimate = vec_madd( estimate, oneHalf, zero );
	estimate = vec_madd( vec_nmsub( v, estimateSquared, one ), halfEstimate, estimate );
	
	estimateSquared = vec_madd( estimate, estimate, zero );
	halfEstimate = vec_madd( estimate, oneHalf, zero );
	estimate = vec_madd( vec_nmsub( v, estimateSquared, one ), halfEstimate, estimate );
	
	estimateSquared = vec_madd( estimate, estimate, zero );
	halfEstimate = vec_madd( estimate, oneHalf, zero );
	return vec_madd( vec_nmsub( v, estimateSquared, one ), halfEstimate, estimate );
	
}

inline vector float vecSquareRoot( vector float v )
{
	return vec_madd( v, vecReciprocalSquareRoot( v ), (vector float)(0) );
}

#endif
