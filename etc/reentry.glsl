#version 130

uniform vec2 iResolution;
uniform vec2 iMouse;


float sdSphere(vec2 p, float r) {
  return length(p) - r;
}

vec2 getSphereNormal(vec2 p) {
  return normalize(p);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  vec2 uv = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
  float aa = fwidth(uv.y);
  float sphereRadius = 0.22;
  float shockRadius = 0.1;
  float dSphere = sdSphere(uv, sphereRadius);
  float shockDist = abs(dSphere - shockRadius);
  float layer = 0.025;
  float sphereMask = smoothstep(aa, 0.0, dSphere);
  vec2 surfNorm = getSphereNormal(uv);
  vec2 windTo = -normalize(iMouse.xy - 0.5 * iResolution.xy);
  float sinT = uv.y / length(uv);
  float cosT = uv.x / length(uv);
  float parabola = (-cosT + sqrt(4 - 3 * cosT * cosT)) / (2 * sinT * sinT);
  float shockWave = parabola * (sphereRadius + shockRadius);
  float shockMask = max(0, layer - abs(shockWave - length(uv))) / layer;
  vec3 col = vec3(clamp(sphereMask + shockMask, 0.0, 1.0));
  fragColor = vec4(col, 1.0);
}
