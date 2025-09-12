#version 410 core

// Based on fire shader https://www.shadertoy.com/view/XsXSWS

uniform vec2 iResolution;
uniform float iTime;

vec2 hash2d( vec2 p )
{
  p = vec2( dot(p,vec2(127.1,311.7)),
       dot(p,vec2(269.5,183.3)) );
  return -1.0 + 2.0*fract(sin(p)*43758.5453123);
}

float noise( in vec2 p )
{
  const float K1 = 0.366025404; // (sqrt(3)-1)/2;
  const float K2 = 0.211324865; // (3-sqrt(3))/6;

  vec2 i = floor( p + (p.x+p.y)*K1 );

  vec2 a = p - i + (i.x+i.y)*K2;
  vec2 o = (a.x>a.y) ? vec2(1.0,0.0) : vec2(0.0,1.0);
  vec2 b = a - o + K2;
  vec2 c = a - 1.0 + 2.0*K2;

  vec3 h = max( 0.5-vec3(dot(a,a), dot(b,b), dot(c,c) ), 0.0 );

  vec3 n = h*h*h*h*vec3( dot(a,hash2d(i+0.0)), dot(b,hash2d(i+o)), dot(c,hash2d(i+1.0)));

  return dot( n, vec3(70.0) );
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2 * fragCoord.y / iResolution.y - 1.0);
  float left = uv.x;
  float t = iTime;
  float brightness = noise(vec2(uv.x * 2 - t * 1, uv.y * 10));
  vec3 color = vec3(brightness, brightness, brightness);
  fragColor = vec4(color, 1);
}
