package com.rcmiku.music.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** The platform Slider owns gestures and accessibility; this draws only its track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveSliderTrack(state:SliderState,playing:Boolean){
    val phase=remember{Animatable(0f)}
    val amplitude=animateFloatAsState(if(playing)1f else 0f,label="wave amplitude")
    LaunchedEffect(playing){if(playing)while(true){phase.snapTo(0f);phase.animateTo((2*PI).toFloat(),tween(1800,easing=LinearEasing))}}
    val active=MaterialTheme.colorScheme.primary
    val inactive=MaterialTheme.colorScheme.secondaryContainer
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(20.dp)){
        val fraction=((state.value-state.valueRange.start)/(state.valueRange.endInclusive-state.valueRange.start).coerceAtLeast(1f)).coerceIn(0f,1f)
        val thumb=size.width*fraction;val gap=6.dp.toPx();val stroke=3.dp.toPx();val y=size.height/2
        scale(if(rtl)-1f else 1f,1f){
            val end=(thumb-gap).coerceAtLeast(0f)
            if(end>0){
                val path=Path();val wavelength=24.dp.toPx();val amp=2.dp.toPx()*amplitude.value
                var x=0f
                while(x<=end){val taper=min(1f,(end-x)/gap);val point=y+sin(x/wavelength*2*PI-phase.value).toFloat()*amp*taper
                    if(x==0f)path.moveTo(x,point)else path.lineTo(x,point);x+=1.dp.toPx()
                }
                path.lineTo(end,y);drawPath(path,active,style=Stroke(stroke,cap=StrokeCap.Round))
            }
            val start=(thumb+gap).coerceAtMost(size.width)
            if(start<size.width){drawLine(inactive,Offset(start,y),Offset(size.width,y),stroke,StrokeCap.Round);drawCircle(active,2.dp.toPx(),Offset(size.width,y))}
        }
    }
}
