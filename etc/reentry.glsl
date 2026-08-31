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
  // Billig's model
  // https://share.gemini.google/kvJVQsbTIRqJ
  float sphereRadius = 0.22;
  float apex = 0.1;
  float layer = 0.025;
  float dSphere = sdSphere(uv, sphereRadius);
  float sphereMask = smoothstep(aa, 0.0, dSphere);
  float shockWave = dSphere - apex;
  float shockMask = max(0, layer - abs(shockWave)) / layer;
  vec3 col = vec3(clamp(sphereMask + shockMask, 0.0, 1.0));
  fragColor = vec4(col, 1.0);
}
