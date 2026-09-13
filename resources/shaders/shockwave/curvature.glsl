#version 450 core

float curvature(vec4 normal, float max_result)
{
  vec3 dNdx = dFdx(normal.xyz);
  vec3 dNdy = dFdy(normal.xyz);
  float k = sqrt(max(dot(dNdx, dNdx), dot(dNdy, dNdy)));
  return normal.z / max(k, 1.0 / max_result);
}
