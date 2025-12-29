#version 450 core

uniform float radius;
uniform float max_height;
uniform vec3 light_direction;
uniform float object_distance;

vec2 limit_interval(vec2 interval, float limit);

<% (if outer %>
vec4 sample_rcs_outer(vec3 object_origin, vec3 object_direction);
<% %>
vec4 sample_rcs_point(vec3 object_origin, vec3 object_direction, float dist);
<% ) %>
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, vec2 segment, vec4 incoming);

<% (if outer %>
vec4 rcs_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
<% %>
vec4 rcs_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist)
<% ) %>
{
<% (if outer %>
  vec4 rcs = sample_rcs_outer(object_origin, object_direction);
<% %>
  vec4 rcs = sample_rcs_point(object_origin, object_direction, dist);
<% ) %>
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere = limit_interval(atmosphere, object_distance);
  rcs.rgb = attenuation_track(light_direction, origin, direction, atmosphere, rcs).rgb;
  return rcs;
}
