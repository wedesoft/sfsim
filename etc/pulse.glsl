#version 450 core
uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;
// https://www.shadertoy.com/view/XsyGzD
void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
  vec2 uv = (-iResolution.xy + 2.0 * fragCoord.xy) / iResolution.y;
  vec2 uv2 = uv;
  //Asin a + B sin 2a +C sin 3a +D sin 4a
  uv2.x += iResolution.x/iResolution.y;
  uv2.x -= 2.0*mod(iTime,1.0*iResolution.x/iResolution.y);
  float width = -(1.0/(25.0*uv2.x));
  vec3 l = vec3(width , width* 1.9, width * 1.5);

  uv.y *= 2.0;
  float xx = abs(1.0/(20.0*max(abs(uv.x),0.3)));

  uv.x *=3.0;
  uv.y -= xx*(sin(uv.x)+3.0*sin(2.0*uv.x)+2.0*sin(3.0*uv.x)+sin(4.0*uv.x));//0.3*sin(uv.x)+0.2*sin(uv.x*2.0)+0.1*sin(uv.x*3.0)+0.1*sin(uv.x*4.0);
  vec3 col = mix(vec3(1),vec3(0),smoothstep(0.02,0.03,abs(uv.y)));
  fragColor = vec4(col*l,1.0);
}
