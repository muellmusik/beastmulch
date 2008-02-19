/*
 *  SDWUGens.cpp
 *  myxSC3plugins
 *
 *  Created by Scott Wilson on 12/08/2005.
 *  Copyright 2005 __MyCompanyName__. All rights reserved.
 *
 */

#include <limits.h>
#include "SC_PlugIn.h"
#include <vecLib/vDSP.h>
#include "SC_Altivec.h"
#include "fastInverseSqrt.h"



//////////////////////////////////////////////////////////////////////////////////////////////////

// macros to put rgen state in registers
#define RGET \
RGen& rgen = *unit->mParent->mRGen; \
uint32 s1 = rgen.s1; \
uint32 s2 = rgen.s2; \
uint32 s3 = rgen.s3; 

#define RPUT \
rgen.s1 = s1; \
rgen.s2 = s2; \
rgen.s3 = s3;

//////////////////////////////////////////////////////////////////////////////////////////////////


// InterfaceTable contains pointers to functions in the host (server).
static InterfaceTable *ft;

// declare struct to hold unit generator state
struct BufUnit : public Unit
{
	SndBuf *m_buf;
	float m_fbufnum;
};

struct TableLookup : public BufUnit
{
	double m_cpstoinc, m_radtoinc;
	int32 mTableSize;
	int32 m_lomask;
};

struct BEOsc : public TableLookup
{
	int32 m_phase;
	float m_phasein;
	//float mLevel; // for BrownNoise
	float m_x1, m_x2, m_x3; // for 4 pt avg
};

//struct BERingz : public Unit
//{
//	float m_y1, m_y2, m_b1, m_b2, m_freq, m_decayTime;
//	float m_x1, m_x2, m_x3; // for 4 pt avg
//};

struct FastSqrt : public Unit
{
};
	
struct LP4PAv : public Unit
{
	float m_x1, m_x2, m_x3;
};

struct LP4Noise : public Unit
{
	float m_x1, m_x2, m_x3;
};

struct LorisMod : public Unit
{
	float m_x1, m_x2, m_x3;
};

struct LorisBW : public Unit {};

struct CheckBadValues : public Unit {};

struct ZapGremlins : public Unit {};

// declare unit generator functions 
extern "C"
{
	void load(InterfaceTable *inTable);
	
	void BEOsc_next_ikk(BEOsc *unit, int inNumSamples);
	void vBEOsc_next_ikk(BEOsc *unit, int inNumSamples);
	void BEOsc_next_ikaa(BEOsc *unit, int inNumSamples);
	void BEOsc_next_ikak(BEOsc *unit, int inNumSamples);
	void BEOsc_next_iaka(BEOsc *unit, int inNumSamples);
	void BEOsc_next_iakk(BEOsc *unit, int inNumSamples);
	void BEOsc_next_iaak(BEOsc *unit, int inNumSamples);
	void BEOsc_next_iaaa(BEOsc *unit, int inNumSamples);
	void BEOsc_Ctor(BEOsc* unit);
	
//	void BERingz_next(BERingz *unit, int inNumSamples);
//	void BERingz_Ctor(BERingz* unit);
	
	void LP4PAv_Ctor(LP4PAv* unit);
	void LP4PAv_next(LP4PAv* unit, int inNumSamples);
	
	void FastSqrt_Ctor(FastSqrt* unit);
	void FastSqrt_next_a(FastSqrt* unit, int inNumSamples);
	void vFastSqrt_next_a(FastSqrt* unit, int inNumSamples);
	void FastSqrt_next_k(FastSqrt* unit, int inNumSamples);
	
	void LP4Noise_Ctor(LP4Noise* unit);
	void LP4Noise_next(LP4Noise* unit, int inNumSamples);
	
	void LorisMod_Ctor(LorisMod* unit);
	void LorisMod_next(LorisMod* unit, int inNumSamples);
	
	void LorisBW_Ctor(LorisBW* unit);
	void LorisBW_next(LorisBW* unit, int inNumSamples);
	
	void CheckBadValues_Ctor(CheckBadValues* unit);
	void CheckBadValues_next(CheckBadValues* unit, int inNumSamples);
	
	void ZapGremlins_Ctor(ZapGremlins* unit);
	void ZapGremlins_next(ZapGremlins* unit, int inNumSamples);
	
};

//////////////////////////////////////////////////////////////////

void BEOsc_Ctor(BEOsc* unit)
{
	
	int tableSize2 = ft->mSineSize;
	unit->m_phasein = ZIN0(1);
	unit->m_radtoinc = tableSize2 * (rtwopi * 65536.); 
	unit->m_cpstoinc = tableSize2 * SAMPLEDUR * 65536.; 
	unit->m_lomask = (tableSize2 - 1) << 3; 
	
	//if (INRATE(2) == calc_FullRate) Print("Audio bw\n");
	if (INRATE(0) == calc_FullRate) {	// freq audio rate
		if (INRATE(1) == calc_FullRate) {	// freq and phase audio rate
			if (INRATE(2) == calc_FullRate) {
				//Print("next_iaaa\n");
				SETCALC(BEOsc_next_iaaa); // ar bandwidth
				unit->m_phase = 0;
			} else {
				//Print("next_iaak\n");
				SETCALC(BEOsc_next_iaak); // kr bandwidth
				unit->m_phase = 0;
			}
		} else {						// freq audio phase control or scalar
			if (INRATE(2) == calc_FullRate) {
				//Print("next_iaka\n");
				SETCALC(BEOsc_next_iaka);	// ar bandwidth
				unit->m_phase = 0;
			} else {
				//Print("next_iakk\n");
				SETCALC(BEOsc_next_iakk);	// kr bandwidth
				unit->m_phase = 0;
			}
		}
	} else {							
		if (INRATE(1) == calc_FullRate) {	// freq control or scalar, phase audio
			if (INRATE(2) == calc_FullRate) {
				//Print("next_ikaa\n");
				SETCALC(BEOsc_next_ikaa); // ar bandwidth
				unit->m_phase = 0;
			} else {
				//Print("next_ikak\n");
				SETCALC(BEOsc_next_ikak); // kr bandwidth
				unit->m_phase = 0;
			}
				
		} else {
#if __VEC__
			if (USEVEC) {					// freq and phase control use vec
				//Print("next_ikk VEC\n");
				SETCALC(vBEOsc_next_ikk);
			} else {						// freq and phase control no vec
				//Print("next_ikk\n");
				SETCALC(BEOsc_next_ikk);
			}
#else
			//Print("next_ikk\n");
			SETCALC(BEOsc_next_ikk);	// freq and phase control no vec
#endif
			unit->m_phase = (int32)(unit->m_phasein * unit->m_radtoinc);
		}
	}
	//unit->mLevel = unit->mParent->mRGen->frand2(); // get a val for BrownNoise
	
	RGET
	unit->m_x1 = frand2(s1, s2, s3);
	unit->m_x2 = frand2(s1, s2, s3);
	unit->m_x3 = frand2(s1, s2, s3);
	RPUT
	BEOsc_next_ikk(unit, 1);
}


//////////////////////////////////////////////////////////////////

// The calculation function executes once per control period 
// which is typically 64 samples.

void BEOsc_next_ikk(BEOsc *unit, int inNumSamples)
{
	float *out = ZOUT(0);
	float freqin = ZIN0(0);
	float phasein = ZIN0(1);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	int32 freq = (int32)(unit->m_cpstoinc * freqin);
	int32 phaseinc = freq + (int32)(CALCSLOPE(phasein, unit->m_phasein) * unit->m_radtoinc);
	unit->m_phasein = phasein;
	
	LOOP(inNumSamples,
		 ZXP(out) = lookupi1(table0, table1, phase, lomask);
		 phase += phaseinc;
		 );
	unit->m_phase = phase;
	
}

#if __VEC__

void vBEOsc_next_ikk(BEOsc *unit, int inNumSamples)
{
	define_vzero
	vfloat32 *vout = (vfloat32*)OUT(0);
	float freqin = ZIN0(0);
	float phasein = ZIN0(1);
	float bwin = ZIN0(2);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	int32 freq = (int32)(unit->m_cpstoinc * freqin);
	int32 phaseinc = freq + (int32)(CALCSLOPE(phasein, unit->m_phasein) * unit->m_radtoinc);
	unit->m_phasein = phasein;
	
	vint32 vphase = vload(phase, phase+phaseinc, phase+2*phaseinc, phase+3*phaseinc);
	vint32 vphaseinc = vload(phaseinc << 2);
	vint32 v3F800000 = (vint32)vinit(0x3F800000);
	vint32 v007FFF80 = (vint32)vinit(0x007FFF80);
	vint32 vlomask = vload(lomask);
	vuint32 vxlobits1 = (vuint32)vinit(xlobits1);
	vuint32 v7 = (vuint32)vinit(7);
	
	vint32 vtable0 = vload((int32)table0); // assuming 32 bit pointers
	vint32 vtable1 = vload((int32)table1); // assuming 32 bit pointers
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	//vfloat32 vbw = vload(bw);
	vfloat32 bw1, bw2; 
	
	// bw coefficients
	bw1 = vload(FastScalarSqrt( 1.f - bwin ));
	bw2 = vload(FastScalarSqrt( 2.f * bwin ));
	
	// * (bw1 + ( mod * bw2 ))
	RGET
	
	int len = inNumSamples << 2;
	for (int i=0; i<len; i+=16) {
		
		vec_union mod;
		x0 = frand2(s1, s2, s3);
		mod.f[0] = (0.25f * (x0 + x1 + x2 + x3));
		x3 = frand2(s1, s2, s3);
		mod.f[1] = (0.25f * (x0 + x1 + x2 + x3));
		x2 = frand2(s1, s2, s3);
		mod.f[2] = (0.25f * (x0 + x1 + x2 + x3));
		x1 = frand2(s1, s2, s3);
		mod.f[3] = (0.25f * (x0 + x1 + x2 + x3));
		
		vfloat32 noise = vec_madd(bw2, mod.vf, bw1);
		
		vfloat32 vfrac = (vfloat32)(vec_or(v3F800000, vec_and(v007FFF80, vec_sl(vphase, v7))));
		vint32 vindex = vec_and(vec_sr(vphase, vxlobits1), vlomask);
		vec_union vaddr0, vaddr1;
		vaddr0.vi = vec_add(vindex, vtable0);
		vaddr1.vi = vec_add(vindex, vtable1);
		
		vec_union vval1, vval2;
		vval1.f[0] = *(float*)(vaddr0.i[0]);
		vval2.f[0] = *(float*)(vaddr1.i[0]);
		vval1.f[1] = *(float*)(vaddr0.i[1]);
		vval2.f[1] = *(float*)(vaddr1.i[1]);
		vval1.f[2] = *(float*)(vaddr0.i[2]);
		vval2.f[2] = *(float*)(vaddr1.i[2]);
		vval1.f[3] = *(float*)(vaddr0.i[3]);
		vval2.f[3] = *(float*)(vaddr1.i[3]);
		
		//vec_st(vec_mul(vec_madd(vval2.vf, vfrac, vval1.vf), noise), i, vout);
		vfloat32 result = vec_mul(vec_madd(vval2.vf, vfrac, vval1.vf), noise);
		vec_st(result, i, vout);
		if(vec_any_nan(result)) Print("NaN detected in vBEOsc_next_ikk\n");
		
		vphase = vec_add(vphase, vphaseinc);

	}
	unit->m_phase = phase + inNumSamples * phaseinc;
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	RPUT
	
}

#endif

void BEOsc_next_ikaa(BEOsc *unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	float freqin = ZIN0(0);
	float *phasein = ZIN(1);
	float *bwin = ZIN(2);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	int32 freq = (int32)(unit->m_cpstoinc * freqin);
	float radtoinc = unit->m_radtoinc;
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	RGET
	//float mod = unit->mLevel; //old noise val
	float mod;
	float bw;
	
	//Print("BEOsc_next_ika %d %g %d\n", inNumSamples, radtoinc, phase);
	// unroll by 4
	LOOP(inNumSamples >> 2,
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += freq;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x3 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += freq;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x2 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += freq;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x1 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += freq;
		 
		 );
	
	// in case of remainder
	LOOP(inNumSamples & 3, 
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += freq;
		 
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 )
		
	unit->m_phase = phase;
	//unit->m_phasein = phasein;
	//unit->mLevel = mod;
	
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	
	RPUT
	
}

void BEOsc_next_ikak(BEOsc *unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	float freqin = ZIN0(0);
	float *phasein = ZIN(1);
	float bwin = ZIN0(2);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	int32 freq = (int32)(unit->m_cpstoinc * freqin);
	float radtoinc = unit->m_radtoinc;
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	float bw1, bw2; 
	
	// bw coefficients
	bw1 = FastScalarSqrt( 1.f - bwin );
	bw2 = FastScalarSqrt( 2.f * bwin );
	
	RGET
		//float mod = unit->mLevel; //old noise val
	float mod;
	
	//Print("BEOsc_next_ika %d %g %d\n", inNumSamples, radtoinc, phase);
//	LOOP(inNumSamples,
//		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
//		 //noise
//		 mod += frand8(s1, s2, s3);
//		 if (mod > 1.f) mod = 2.f - mod; 
//		 else if (mod < -1.f) mod = -2.f - mod;
//		 
//		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
//		 phase += freq;
//		 );
	// unroll by 4
	LOOP(inNumSamples >> 2,
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += freq;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x3 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += freq;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x2 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += freq;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x1 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += freq;
		 );
	// in case of remainder
	LOOP(inNumSamples & 3, 
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 ZXP(out) = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += freq;
		 
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 )
	unit->m_phase = phase;
	//unit->m_phasein = phasein;
	//unit->mLevel = mod;
	
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	
	RPUT
		
}

void BEOsc_next_iaaa(BEOsc *unit, int inNumSamples)
{
	float *out = ZOUT(0);
	float *freqin = ZIN(0);
	float *phasein = ZIN(1);
	float *bwin = ZIN(2);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	float cpstoinc = unit->m_cpstoinc;
	float radtoinc = unit->m_radtoinc;
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	RGET
	//float mod = unit->mLevel; //old noise val
	float mod;
	float bw;
	//Print("BEOsc_next_iaa %d %g %g %d\n", inNumSamples, cpstoinc, radtoinc, phase);
	
	// unroll by 4
	LOOP(inNumSamples >> 2,
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 float z = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x3 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 z = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x2 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 z = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x1 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 z = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 );
	// remainder
	LOOP(inNumSamples & 3,
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 
		 float z = lookupi1(table0, table1, phaseoffset, lomask) * (FastScalarSqrt( 1.f - bw ) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 );
	
	unit->m_phase = phase;
	//unit->mLevel = mod;
	RPUT
		
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	//unit->m_phasein = ZX(phasein);
	
}

void BEOsc_next_iaak(BEOsc *unit, int inNumSamples)
{
	float *out = ZOUT(0);
	float *freqin = ZIN(0);
	float *phasein = ZIN(1);
	float bwin = ZIN0(2);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	float cpstoinc = unit->m_cpstoinc;
	float radtoinc = unit->m_radtoinc;
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	RGET
	//float mod = unit->mLevel; //old noise val
	float bw1, bw2, mod; 
	
	// bw coefficients
	bw1 = FastScalarSqrt( 1.f - bwin );
	bw2 = FastScalarSqrt( 2.f * bwin );
	
	//Print("BEOsc_next_iaa %d %g %g %d\n", inNumSamples, cpstoinc, radtoinc, phase);
	
	// unroll by 4
	LOOP(inNumSamples >> 2,
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 
		 float z = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x3 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 
		 z = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x2 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 
		 z = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x1 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 
		 z = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 );
	// remainder
	LOOP(inNumSamples & 3,
		 int32 phaseoffset = phase + (int32)(radtoinc * ZXP(phasein));
		 //noise
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 
		 float z = lookupi1(table0, table1, phaseoffset, lomask) * (bw1 + ( mod * bw2 ));
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 ZXP(out) = z;
		 
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 );
	
	unit->m_phase = phase;
	//unit->mLevel = mod;
	RPUT
		
		unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	//unit->m_phasein = ZX(phasein);
	
}

//void BEOsc_next_iak(BEOsc *unit, int inNumSamples)
//{
//	
//	float *out = ZOUT(0);
//	float *freqin = ZIN(0);
//	float phasein = ZIN0(1);
//	float *bwin = ZIN(2);
//	
//	float *table0 = ft->mSineWavetable;
//	float *table1 = table0 + 1;
//	
//	int32 phase = unit->m_phase;
//	int32 lomask = unit->m_lomask;
//	
//	float cpstoinc = unit->m_cpstoinc;
//	float radtoinc = unit->m_radtoinc;
//	float phasemod = unit->m_phasein;
//	float phaseslope = CALCSLOPE(phasein, phasemod);
//	
//	RGET
//	float mod = unit->mLevel; //old noise val
//	
//	LOOP(inNumSamples,
//		 int32 pphase = phase + (int32)(radtoinc * phasemod);
//		 phasemod += phaseslope;
//		 
//		 //noise
//		 mod += frand8(s1, s2, s3);
//		 if (mod > 1.f) mod = 2.f - mod; 
//		 else if (mod < -1.f) mod = -2.f - mod;
//		 float bw = ZXP(bwin);
//		 float z = lookupi1(table0, table1, pphase, lomask);
//		 phase += (int32)(cpstoinc * ZXP(freqin));
//		 
//		 ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
//		 );
//
//	unit->mLevel = mod;
//	RPUT
//	unit->m_phase = phase;
//	unit->m_phasein = phasein;
//}
	
// try to do noise separately, vec attempt
//	void BEOsc_next_iak(BEOsc *unit, int inNumSamples)
//{
//		
//		float *out = ZOUT(0);
//		float noise[inNumSamples];
//		float sine[inNumSamples];
//		float *freqin = ZIN(0);
//		float phasein = ZIN0(1);
//		float *bwin = ZIN(2);
//		
//		float *table0 = ft->mSineWavetable;
//		float *table1 = table0 + 1;
//		
//		int32 phase = unit->m_phase;
//		int32 lomask = unit->m_lomask;
//		
//		float cpstoinc = unit->m_cpstoinc;
//		float radtoinc = unit->m_radtoinc;
//		float phasemod = unit->m_phasein;
//		float phaseslope = CALCSLOPE(phasein, phasemod);
//		
//		int i = 0;
//		
//		RGET
//			float mod = unit->mLevel; //old noise val
//		
//		LOOP(inNumSamples,
//			 
//			 //noise
//			 mod += frand8(s1, s2, s3);
//			 if (mod > 1.f) mod = 2.f - mod; 
//			 else if (mod < -1.f) mod = -2.f - mod;
//			 //float bw = sc_clip((ZXP(bwin)), -0.f, 1.f);
//			 float bw = ZXP(bwin);
//
//			 noise[i] = (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
//			 ++i;
//			 );
//		
//		unit->mLevel = mod;
//		RPUT
//		i = 0;
//		LOOP(inNumSamples,
//			 int32 pphase = phase + (int32)(radtoinc * phasemod);
//			 phasemod += phaseslope;
//			 
//			 float z = lookupi1(table0, table1, pphase, lomask);
//			 //float z = lookupi1(table0, table1, pphase, lomask) * 1.f;
//			 phase += (int32)(cpstoinc * ZXP(freqin));
//			 //ZXP(out) = z * (sqrt( 1.f - sc_min(bw, 1.f)) + ( mod * sqrt( 2.f * sc_max(bw, 0.f) ) ));
//			 sine[i] = z;
//			 ++i;
//			 );
//		
//		vmul(noise, 1, sine, 1, out, 1, inNumSamples);
//			
//		unit->m_phase = phase;
//		unit->m_phasein = phasein;
//}

// version with whitenoise/ unrolled average filter
void BEOsc_next_iaka(BEOsc *unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	float *freqin = ZIN(0);
	float phasein = ZIN0(1);
	float *bwin = ZIN(2);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	float cpstoinc = unit->m_cpstoinc;
	float radtoinc = unit->m_radtoinc;
	float phasemod = unit->m_phasein;
	float phaseslope = CALCSLOPE(phasein, phasemod);
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	float z, bw, mod;
	float final;
	int32 pphase;
	
	RGET
		//float mod = unit->mLevel; //old noise val
	
	// unroll by 4	
	LOOP(inNumSamples >> 2,
		 
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 pphase = phase + (int32)(radtoinc * phasemod);
		 phasemod += phaseslope;
		 z = lookupi1(table0, table1, pphase, lomask);
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
		 //ZXP(out) = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 final = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 ZXP(out) = final;
		 checkBadValues(final);
		 
		 x3 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 pphase = phase + (int32)(radtoinc * phasemod);
		 phasemod += phaseslope;
		 z = lookupi1(table0, table1, pphase, lomask);
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
		 //ZXP(out) = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 final = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 ZXP(out) = final;
		 checkBadValues(final);
		 
		 x2 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 pphase = phase + (int32)(radtoinc * phasemod);
		 phasemod += phaseslope;
		 z = lookupi1(table0, table1, pphase, lomask);
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
		 //ZXP(out) = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 final = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 ZXP(out) = final;
		 checkBadValues(final);
		 
		 x1 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 pphase = phase + (int32)(radtoinc * phasemod);
		 phasemod += phaseslope;
		 z = lookupi1(table0, table1, pphase, lomask);
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
		 //ZXP(out) = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 final = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 ZXP(out) = final;
		 checkBadValues(final);
		 );
	// in case of remainder
	LOOP(inNumSamples & 3, 
		 printf("remain\n");
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 bw = ZXP(bwin);
		 pphase = phase + (int32)(radtoinc * phasemod);
		 phasemod += phaseslope;
		 z = lookupi1(table0, table1, pphase, lomask);
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
		 //ZXP(out) = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 final = z * (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
		 ZXP(out) = final;
		 checkBadValues(final);
		 
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 )
	
	//unit->mLevel = mod;
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	
	RPUT
	
	unit->m_phase = phase;
	unit->m_phasein = phasein;
}

// control rate bw
void BEOsc_next_iakk(BEOsc *unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	float *freqin = ZIN(0);
	float phasein = ZIN0(1);
	float bwin = ZIN0(2);
	
	float *table0 = ft->mSineWavetable;
	float *table1 = table0 + 1;
	
	int32 phase = unit->m_phase;
	int32 lomask = unit->m_lomask;
	
	float cpstoinc = unit->m_cpstoinc;
	float radtoinc = unit->m_radtoinc;
	float phasemod = unit->m_phasein;
	float phaseslope = CALCSLOPE(phasein, phasemod);
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	float z, mod;
	float bw1, bw2; 
	int32 pphase;
	
	// bw coefficients
	bw1 = FastScalarSqrt( 1.f - bwin );
	bw2 = FastScalarSqrt( 2.f * bwin );
	
	RGET
		//float mod = unit->mLevel; //old noise val
		
		// unroll by 4	
		LOOP(inNumSamples >> 2,
			 
			 x0 = frand2(s1, s2, s3);
			 mod = 0.25f * (x0 + x1 + x2 + x3);
			 pphase = phase + (int32)(radtoinc * phasemod);
			 phasemod += phaseslope;
			 z = lookupi1(table0, table1, pphase, lomask);
			 phase += (int32)(cpstoinc * ZXP(freqin));
			 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
			 ZXP(out) = z * (bw1 + ( mod * bw2 ));
			 
			 x3 = frand2(s1, s2, s3);
			 mod = 0.25f * (x0 + x1 + x2 + x3);
			 pphase = phase + (int32)(radtoinc * phasemod);
			 phasemod += phaseslope;
			 z = lookupi1(table0, table1, pphase, lomask);
			 phase += (int32)(cpstoinc * ZXP(freqin));
			 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
			 ZXP(out) = z * (bw1 + ( mod * bw2 ));
			 
			 x2 = frand2(s1, s2, s3);
			 mod = 0.25f * (x0 + x1 + x2 + x3);
			 pphase = phase + (int32)(radtoinc * phasemod);
			 phasemod += phaseslope;
			 z = lookupi1(table0, table1, pphase, lomask);
			 phase += (int32)(cpstoinc * ZXP(freqin));
			 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
			 ZXP(out) = z * (bw1 + ( mod * bw2 ));
			 
			 x1 = frand2(s1, s2, s3);
			 mod = 0.25f * (x0 + x1 + x2 + x3);
			 pphase = phase + (int32)(radtoinc * phasemod);
			 phasemod += phaseslope;
			 z = lookupi1(table0, table1, pphase, lomask);
			 phase += (int32)(cpstoinc * ZXP(freqin));
			 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
			 ZXP(out) = z * (bw1 + ( mod * bw2 ));
			 );
	// in case of remainder
	LOOP(inNumSamples & 3, 
		 printf("remain\n");
		 x0 = frand2(s1, s2, s3);
		 mod = 0.25f * (x0 + x1 + x2 + x3);
		 pphase = phase + (int32)(radtoinc * phasemod);
		 phasemod += phaseslope;
		 z = lookupi1(table0, table1, pphase, lomask);
		 phase += (int32)(cpstoinc * ZXP(freqin));
		 //ZXP(out) = z * (sc_sqrt( 1.f - bw) + ( mod * sc_sqrt( 2.f * bw ) ));
		 ZXP(out) = z * (bw1 + ( mod * bw2 ));
		 
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 )
		
	//unit->mLevel = mod;
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	
	RPUT
		
		unit->m_phase = phase;
	unit->m_phasein = phasein;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////


void LP4PAv_Ctor(LP4PAv* unit)
{	
	//postbuf("LPZ2_Reset\n");
	SETCALC(LP4PAv_next);
	unit->m_x1 = unit->m_x2 = unit->m_x3 = ZIN0(0);
	ZOUT0(0) = 0.f;
}


void LP4PAv_next(LP4PAv* unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	float *in = ZIN(0);
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	// unroll by 4
	LOOP(inNumSamples >> 2,
		 x0 = ZXP(in); 
		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
		 x3 = ZXP(in); 
		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
		 x2 = ZXP(in); 
		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
		 x1 = ZXP(in); 
		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
		 );
	// in case of remainder
	LOOP(inNumSamples & 3, 
		 x0 = ZXP(in); 
		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 );
	
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////

void FastSqrt_Ctor(FastSqrt* unit)
{
	if(INRATE(0) == calc_FullRate) {
#if __VEC__
		if(USEVEC) {
			//Print("vec FastSqrt_a\n");
			SETCALC(vFastSqrt_next_a);
		} else {
			//Print("FastSqrt_a\n");
			SETCALC(FastSqrt_next_a);
		}
#else
		//Print("FastSqrt_a\n");
		SETCALC(FastSqrt_next_a);
#endif
	} else {
		//Print("FastSqrt_k\n");
		SETCALC(FastSqrt_next_k);
	}
}

void FastSqrt_next_a(FastSqrt* unit, int inNumSamples)
{
	float *out = ZOUT(0);
	float *in = ZIN(0);
	
// scalar slightly faster by 3s	
	LOOP(unit->mRate->mFilterLoops,
		 ZXP(out) = FastScalarSqrt(ZXP(in));
		 ZXP(out) = FastScalarSqrt(ZXP(in));
		 ZXP(out) = FastScalarSqrt(ZXP(in));
		 )
	LOOP(unit->mRate->mFilterRemain,
		 ZXP(out) = FastScalarSqrt(ZXP(in));
		 )	
}

#if __VEC__
void vFastSqrt_next_a(FastSqrt* unit, int inNumSamples)
{
	vfloat32 *vout = (vfloat32*)OUT(0);
	vfloat32 *vin = (vfloat32*)IN(0);
	
	int len = inNumSamples << 2;
	for (int i=0; i<len; i+=16) {
		// protect against negative numbers
		vec_st(vecSquareRoot( vec_abs(vec_ld(i, vin)) ), i, vout);

	}
}
#endif

void FastSqrt_next_k(FastSqrt* unit, int inNumSamples)
{
	float *out = ZOUT(0);
	float sqr = FastScalarSqrt(ZIN0(0));
	
	//checkBadValues(sqr);
	
	LOOP(inNumSamples,
		 ZXP(out) = sqr;
	)	
}

////////////////////////////////////////////////////////////////////////////////////////////////////////

void LP4Noise_Ctor(LP4Noise* unit)
{	
	//postbuf("LPZ2_Reset\n");
	SETCALC(LP4Noise_next);
	RGET
	unit->m_x1 = frand2(s1, s2, s3);
	unit->m_x2 = frand2(s1, s2, s3);
	unit->m_x3 = frand2(s1, s2, s3);
	RPUT
	ZOUT0(0) = 0.f;
}


void LP4Noise_next(LP4Noise* unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	float val;
	
	RGET
		
	// unroll by 4
//	LOOP(inNumSamples >> 2,
//		 x0 = frand2(s1, s2, s3); 
//		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
//		 x3 = frand2(s1, s2, s3); 
//		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
//		 x2 = frand2(s1, s2, s3);
//		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
//		 x1 = frand2(s1, s2, s3); 
//		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
//		 );
	
	LOOP(inNumSamples >> 2,
		 x0 = frand2(s1, s2, s3); 
		 val = 0.25f * (x0 + x1 + x2 + x3);
		 checkBadValues(val);
		 ZXP(out) = val;
		 x3 = frand2(s1, s2, s3); 
		 val = 0.25f * (x0 + x1 + x2 + x3);
		 checkBadValues(val);
		 ZXP(out) = val;
		 x2 = frand2(s1, s2, s3);
		 val = 0.25f * (x0 + x1 + x2 + x3);
		 checkBadValues(val);
		 ZXP(out) = val;
		 x1 = frand2(s1, s2, s3); 
		 val = 0.25f * (x0 + x1 + x2 + x3);
		 checkBadValues(val);
		 ZXP(out) = val;
		 );
	// in case of remainder
	LOOP(inNumSamples & 3, 
		 x0 = frand2(s1, s2, s3); 
		 ZXP(out) = 0.25f * (x0 + x1 + x2 + x3);
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 );
	
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	RPUT
}

////////////////////////////////////////////////////////////////////////////////////////////////////////

void LorisMod_Ctor(LorisMod* unit)
{	
	SETCALC(LorisMod_next);
	RGET
		unit->m_x1 = frand2(s1, s2, s3);
	unit->m_x2 = frand2(s1, s2, s3);
	unit->m_x3 = frand2(s1, s2, s3);
	RPUT
		ZOUT0(0) = 0.f;
}


void LorisMod_next(LorisMod* unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	float *bwin = ZIN(0);
	
	float x0;
	float x1 = unit->m_x1;
	float x2 = unit->m_x2;
	float x3 = unit->m_x3;
	
	float bw;
	
	RGET
		
	// unroll by 4
	LOOP(inNumSamples >> 2,
		 bw = ZXP(bwin);
		 x0 = frand2(s1, s2, s3); 
		 ZXP(out) = (FastScalarSqrt( 1.f - bw) + ( 0.25f * (x0 + x1 + x2 + x3) * FastScalarSqrt( 2.f * bw ) ));
		 bw = ZXP(bwin);
		 x3 = frand2(s1, s2, s3); 
		 ZXP(out) = (FastScalarSqrt( 1.f - bw) + ( 0.25f * (x0 + x1 + x2 + x3) * FastScalarSqrt( 2.f * bw ) ));
		 bw = ZXP(bwin);
		 x2 = frand2(s1, s2, s3);
		 ZXP(out) = (FastScalarSqrt( 1.f - bw) + ( 0.25f * (x0 + x1 + x2 + x3) * FastScalarSqrt( 2.f * bw ) ));
		 bw = ZXP(bwin);
		 x1 = frand2(s1, s2, s3); 
		 ZXP(out) = (FastScalarSqrt( 1.f - bw) + ( 0.25f * (x0 + x1 + x2 + x3) * FastScalarSqrt( 2.f * bw ) ));
		 );
	// in case of remainder
	LOOP(inNumSamples & 3, 
		 bw = ZXP(bwin);
		 x0 = frand2(s1, s2, s3); 
		 ZXP(out) = (FastScalarSqrt( 1.f - bw) + ( 0.25f * (x0 + x1 + x2 + x3) * FastScalarSqrt( 2.f * bw ) ));
		 x3 = x2;
		 x2 = x1;
		 x1 = x0;
		 );
		
	
	unit->m_x1 = x1;
	unit->m_x2 = x2;
	unit->m_x3 = x3;
	RPUT
}

////////////////////////////////////////////////////////////////////////////////////////////////////////

void LorisBW_Ctor(LorisBW* unit)
{	
	SETCALC(LorisBW_next);
	//ZOUT0(0) = 0.f;
	LorisBW_next(unit, 1);
}


void LorisBW_next(LorisBW* unit, int inNumSamples)
{
	
	float *out = ZOUT(0);
	float *in = ZIN(0);
	float *bwin = ZIN(1);
	
	float bw;

	LOOP(inNumSamples,
		 bw = ZXP(bwin);
		 ZXP(out) = (FastScalarSqrt( 1.f - bw) + ( 0.25f * ZXP(in) * FastScalarSqrt( 2.f * bw ) ));
		 );
}

////////////////////////////////////////////////////////////////////////////////////////////////////////

//void BERingz_Ctor(BERingz* unit)
//{	
//	//postbuf("Ringz_Reset\n");
//	SETCALC(BERingz_next);
//	unit->m_b1 = 0.f;
//	unit->m_b2 = 0.f;
//	unit->m_y1 = 0.f;
//	unit->m_y2 = 0.f;
//	unit->m_freq = 0.f;
//	unit->m_decayTime = 0.f;
//	ZOUT0(0) = 0.f;
//}
//
//// not very optimized as noise gen an mFilterloops don't coincide
//// perhaps noise should run in its own loop
//void BERingz_next(BERingz* unit, int inNumSamples)
//{
//	//postbuf("Ringz_next\n");
//	
//	float *out = ZOUT(0);
//	float *in = ZIN(0);
//	float freq = ZIN0(1);
//	float decayTime = ZIN0(2);
//	float *bwin = ZIN(3);
//	
//	float y0;
//	float y1 = unit->m_y1;
//	float y2 = unit->m_y2;
//	float a0 = 0.5f;
//	float b1 = unit->m_b1;
//	float b2 = unit->m_b2;
//	
//	float x0;
//	float x1 = unit->m_x1;
//	float x2 = unit->m_x2;
//	float x3 = unit->m_x3;
//	
//	float mod, bw;
//	float noise[inNumSamples];
//	int i = 0;
//	
//	RGET
//	// filtered noise	
//	// unroll by 4
//	LOOP(inNumSamples >> 2,
//		 x0 = frand2(s1, s2, s3);
//		 bw = ZXP(bwin);
//		 mod = 0.25f * (x0 + x1 + x2 + x3);
//		 noise[i] = (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
//		 ++i;
//		 x3 = frand2(s1, s2, s3); 
//		 bw = ZXP(bwin);
//		 mod = 0.25f * (x0 + x1 + x2 + x3);
//		 noise[i] = (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
//		 ++i;
//		 x2 = frand2(s1, s2, s3);
//		 bw = ZXP(bwin);
//		 mod = 0.25f * (x0 + x1 + x2 + x3);
//		 noise[i] = (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
//		 ++i;
//		 x1 = frand2(s1, s2, s3); 
//		 bw = ZXP(bwin);
//		 mod = 0.25f * (x0 + x1 + x2 + x3);
//		 noise[i] = (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
//		 ++i;
//		 );
//	// in case of remainder
//	LOOP(inNumSamples & 3, 
//		 x0 = frand2(s1, s2, s3);
//		 bw = ZXP(bwin);
//		 mod = 0.25f * (x0 + x1 + x2 + x3);
//		 noise[i] = (FastScalarSqrt( 1.f - bw) + ( mod * FastScalarSqrt( 2.f * bw ) ));
//		 ++i;
//		 x3 = x2;
//		 x2 = x1;
//		 x1 = x0;
//		 );
//	
//	i = 0;
//	if (freq != unit->m_freq || decayTime != unit->m_decayTime) {
//		float ffreq = freq * unit->mRate->mRadiansPerSample;
//		float R = decayTime == 0.f ? 0.f : exp(log001/(decayTime * SAMPLERATE));
//		float twoR = 2.f * R;
//		float R2 = R * R;
//		float cost = (twoR * cos(ffreq)) / (1.f + R2);
//		float b1_next = twoR * cost;
//		float b2_next = -R2;
//		float b1_slope = (b1_next - b1) * unit->mRate->mFilterSlope;
//		float b2_slope = (b2_next - b2) * unit->mRate->mFilterSlope;
//		LOOP(unit->mRate->mFilterLoops,
//			 y0 = ZXP(in) + b1 * y1 + b2 * y2; 
//			 ZXP(out) = a0 * (y0 - y2) * noise[i];
//			 ++i;
//			 
//			 y2 = ZXP(in) + b1 * y0 + b2 * y1; 
//			 ZXP(out) = a0 * (y2 - y1) * noise[i];
//			 ++i;
//			 
//			 y1 = ZXP(in) + b1 * y2 + b2 * y0; 
//			 ZXP(out) = a0 * (y1 - y0) * noise[i];
//			 ++i;
//			 
//			 b1 += b1_slope; 
//			 b2 += b2_slope;
//			 );
//		LOOP(unit->mRate->mFilterRemain,
//			 y0 = ZXP(in) + b1 * y1 + b2 * y2; 
//			 ZXP(out) = a0 * (y0 - y2) * noise[i];
//			 ++i;
//			 y2 = y1; 
//			 y1 = y0;
//			 );
//		
//		unit->m_freq = freq;
//		unit->m_decayTime = decayTime;
//		unit->m_b1 = b1_next;
//		unit->m_b2 = b2_next;
//	} else {
//		LOOP(unit->mRate->mFilterLoops,
//			 y0 = ZXP(in) + b1 * y1 + b2 * y2; 
//			 ZXP(out) = a0 * (y0 - y2) * noise[i];
//			 ++i;
//			 
//			 y2 = ZXP(in) + b1 * y0 + b2 * y1; 
//			 ZXP(out) = a0 * (y2 - y1) * noise[i];
//			 ++i;
//			 
//			 y1 = ZXP(in) + b1 * y2 + b2 * y0; 
//			 ZXP(out) = a0 * (y1 - y0) * noise[i];
//			 ++i;
//			 );
//		LOOP(unit->mRate->mFilterRemain,
//			 y0 = ZXP(in) + b1 * y1 + b2 * y2; 
//			 ZXP(out) = a0 * (y0 - y2) * noise[i];
//			 ++i;
//			 y2 = y1; 
//			 y1 = y0;
//			 );
//	}
//	unit->m_y1 = zapgremlins(y1);
//	unit->m_y2 = zapgremlins(y2);
//	unit->m_x1 = x1;
//	unit->m_x2 = x2;
//	unit->m_x3 = x3;
//	
//	RPUT
//	
//}
	
////////////////////////////////////////////////////////////////////////////////////////////////////////

void CheckBadValues_Ctor(CheckBadValues* unit)
{	
	SETCALC(CheckBadValues_next);
}


void CheckBadValues_next(CheckBadValues* unit, int inNumSamples)
{
	
	
	float *in = ZIN(0);
	float *out = ZOUT(0);
	float id = ZIN0(1);
	
	float samp, output;
	int classification;
	
	LOOP(inNumSamples,
		 samp = ZXP(in);
		 //checkBadValues(samp);
		 classification = fpclassify(samp);
		 switch (classification) 
		 { 
			 case FP_INFINITE: 
				 printf("Infinite number found in Synth %d, ID: %d\n", unit->mParent->mNode.mID, (int)id); 
				 output = 2;
				 break; 
			 case FP_NAN: 
				 printf("NaN found in Synth %d, ID: %d\n", unit->mParent->mNode.mID, (int)id); 
				 output = 1;
				 break; 
			 case FP_SUBNORMAL:
				 printf("Denormal found in Synth %d, ID: %d\n", unit->mParent->mNode.mID, (int)id); 
				 output = 3;
				 break;
			 default: 
				 output = 0;
		 };
		 
		 ZXP(out) = output;
		 );
}

////////////////////////////////////////////////////////////////////

void ZapGremlins_Ctor(ZapGremlins* unit)
{
	SETCALC(ZapGremlins_next);
}	

void ZapGremlins_next(ZapGremlins* unit, int inNumSamples)
{
	float *in = ZIN(0);
	float *out = ZOUT(0);
	
	LOOP(inNumSamples,
		 ZXP(out) = zapgremlins(ZXP(in));
	);
	
}


////////////////////////////////////////////////////////////////////

// the load function is called by the host when the plug-in is loaded
void load(InterfaceTable *inTable)
{
	ft = inTable;
	
	DefineSimpleUnit(BEOsc);
	DefineSimpleUnit(LP4PAv);
	DefineSimpleUnit(FastSqrt);
	DefineSimpleUnit(LP4Noise);
	DefineSimpleUnit(LorisMod);
	DefineSimpleUnit(LorisBW);
	//DefineSimpleUnit(BERingz);
	DefineSimpleUnit(CheckBadValues);
	DefineSimpleUnit(ZapGremlins);
}


