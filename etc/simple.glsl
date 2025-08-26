uniform vec2 iResolution;
uniform float iTime;

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
  vec2 uv = fragCoord / iResolution.xy;
  float time = mod(iTime,22.5);
  if (time < 4.55) {
    fragColor = vec4(uv.x * time,uv.y * time,0,0);
  } else {
    if (time < 10.50) {
      time = time - 4.55;
      fragColor = vec4(uv.x * 4.55,uv.y * 5.0 - time,0,0);
    } else {
      time = time - 10.50;
      fragColor = vec4((uv.x * 4.55) * 2.5 - time,uv.y * -2.0,0,0);
    }
  }
}
