#version 410 core

uniform float <%= epsilon %>;
float <%= function-name %>(vec3 point);

vec3 <%= method-name %>(vec3 point)
{
  float dx = <%= function-name %>(point + vec3(<%= epsilon %>, 0, 0)) - <%= function-name %>(point - vec3(<%= epsilon %>, 0, 0));
  float dy = <%= function-name %>(point + vec3(0, <%= epsilon %>, 0)) - <%= function-name %>(point - vec3(0, <%= epsilon %>, 0));
  float dz = <%= function-name %>(point + vec3(0, 0, <%= epsilon %>)) - <%= function-name %>(point - vec3(0, 0, <%= epsilon %>));
  return vec3(dx, dy, dz) / (2 * <%= epsilon %>);
}
