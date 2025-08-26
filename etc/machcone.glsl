#version 410 core

uniform vec2 iResolution;
uniform float iTime;

// Mach cone shader by xingyzt | https://www.shadertoy.com/view/WdGBDc
// 1D perlin noise by ikuto    | https://www.shadertoy.com/view/lt3BWM
// 2D vesica SDF by iq         | https://www.shadertoy.com/view/XtVfRW

#define HASHSCALE 0.1031

float hash(float p)
{
	vec3 p3  = fract(vec3(p) * HASHSCALE);
    p3 += dot(p3, p3.yzx + 19.19);
    return fract((p3.x + p3.y) * p3.z);
}
float hash31( vec3 p )
{
	float h = dot(p,vec3(17, 1527, 113));	
    return fract(sin(h)*43758.5453123);
}


float fade(float t) { return t*t*t*(t*(6.*t-15.)+10.); }

float grad(float hash, float p)
{
    int i = int(1e4*hash);
	return (i & 1) == 0 ? p : -p;
}

float perlin(float p)
{
	float pi = floor(p), pf = p - pi, w = fade(pf);
    return mix(grad(hash(pi), pf), grad(hash(pi + 1.0), pf - 1.0), w) * 2.0;
}

float vesica(vec2 p, float r, float d)
{
    p = abs(p);

    float b = sqrt(r*r-d*d);  // can delay this sqrt by rewriting the comparison
    return ((p.x-b)*d > p.y*b) ? length(p-vec2(b,0.0))*sign(d)
                               : length(p-vec2(0.0,-d))-r;
}

float mvesica(vec2 p)
{
    return vesica(p,1.,.8);
}

float smin( float a, float b, float k )
{
    float h = clamp( 0.5+0.5*(b-a)/k, 0.0, 1.0 );
    return mix( b, a, h ) - k*h*(1.0-h);
}

float smoothsign( float s, float d )
{
    return smoothstep(-s,s,d)*2.-1.;
}

// Mach Diamonds!

#define PI     3.1416
#define t     iTime
#define start  3.1 // sync with the soundcloud audio
#define end   23.6

float shell(vec2 p,float offset)
{
    float main = mvesica(p);
    float next = min(
        mvesica(p+vec2(offset,0)),
        mvesica(p-vec2(offset,0))
    );
    return smin(
        main,
        next,
        .15
    );
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    float t = mod(iTime,end);

    float offset = .8;

    float left = fragCoord.x/iResolution.x;
    float right = 1.-left;

    vec2 uv = (2.0*fragCoord-vec2(0,iResolution.y))/iResolution.y;
    uv += .03*perlin(t*.5+left)*(.5+left);
    uv += .01*perlin(t*7.+left)*(.5+left);
    uv.y += .01*perlin(t*67.+left)*(.5+left);
    uv.y += .005*perlin(t*101.+left)*(.5+left);
    //uv.y /= sqrt(right)*.5+1.;
    uv.x += .5*(.2-abs(uv.y))*perlin(t*3.);
    offset += .02*perlin(t*3.);
    uv*=1.;

    vec2 p = uv;
    p.x = mod(p.x,offset)-offset/2.;

    float diamonds = max(smin(
        vesica(p-vec2(offset-.4,0),.3,.15),
        max(vesica(p+vec2(offset-.4,0),.85,.7),0.)+.05*(1.-2.*abs(p.x-.1)),
        .1
    ),0.);
    float exhaust = shell(p,offset);
    float streams = 1.;
    for(float i = 0.; i<6.; i++){
        if(perlin(i+t)>.2) continue;
        p.y+=perlin(i-t)*.05;
        streams *= (abs(shell(
            p+vec2(0,sign(p.y)*.005*i*i
        ),offset))<.005) ? 0. : 1.;
    }


    float outside = max(sign(exhaust),0.);
    float inside = 1.-outside;

    float soutside = (1.+smoothsign(.1,exhaust))/2.;
    float sinside = 1.-soutside;

    float d = smin(exhaust,-diamonds,-.03);

    d = abs(d);


    float lum = (
        1.-d
        *(
             2. * inside
            +5. * outside / (left+.2)
        )
        *smoothstep(start,start+1.,t)
        *smoothstep(end,end-1.,t)
    );
    lum = clamp(lum,0.,1.);
    lum = pow(lum,(.9*(inside)+1.5*outside));

    //if (abs(d) < .005) d = 1.;


    vec3 col = vec3(lum);

    col *= vec3(
        (1.05*sinside+1.40*soutside-left*.0),
        (1.00*sinside+1.00*soutside-left*.2),
        (1.40*sinside+1.80*soutside-left*.6)
    );

    col -= diamonds;

    col *= smoothstep(start,start+.1,t)*smoothstep(end,end-.9,t);

    col *= 1.;//-.1*streams*perlin(.5*uv.x-5.*t+perlin(uv.y+uv.x+t));

    //col = vec3(streams);

    fragColor = vec4(col,1);
    //fragColor = vec4(vec3(1.-abs(p.x-.1)),1);

}

