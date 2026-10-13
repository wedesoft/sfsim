#version 450 core

uniform sampler2D points;
uniform mat4 camera_to_shadow;
uniform int overlay_width;
uniform int overlay_height;

out vec4 fragColor;

vec4 geometry_point();
float geometry_distance();
<% (when outer %>
vec4 sample_shockwave_outer(vec3 object_origin, vec3 object_direction);
<% ) %>
<% (when (not outer) %>
vec4 sample_shockwave_point(vec3 object_origin, vec3 object_direction, float dist);
<% ) %>

void main()
{
  vec3 origin = (camera_to_shadow * vec4(0, 0, 0, 1)).xyz;
  vec4 point = geometry_point();
  vec3 direction = normalize((camera_to_shadow * vec4(point.xyz, 0)).xyz);
<% (when outer %>
  fragColor = sample_shockwave_outer(origin, direction);
<% ) %>
<% (when (not outer) %>
  float dist = geometry_distance();
  fragColor = sample_shockwave_point(origin, direction, dist);
<% ) %>
}
