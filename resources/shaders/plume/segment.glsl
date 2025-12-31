#version 450 core

uniform float radius;
uniform float max_height;
uniform vec3 light_direction;
uniform float object_distance;

vec2 limit_interval(vec2 interval, float limit);

<% (when outer %>
vec4 sample_<%= type %>_outer(vec3 object_origin, vec3 object_direction);
<% ) %>
<% (when (not outer) %>
vec4 sample_<%= type %>_point(vec3 object_origin, vec3 object_direction, float dist);
<% ) %>
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, vec2 segment, vec4 incoming);

<% (when outer %>
vec4 <%= type %>_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
<% ) %>
<% (when (not outer) %>
vec4 <%= type %>_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist)
<% ) %>
{
<% (when outer %>
  vec4 result = sample_<%= type %>_outer(object_origin, object_direction);
<% ) %>
<% (when (not outer) %>
  vec4 result = sample_<%= type %>_point(object_origin, object_direction, dist);
<% ) %>
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere = limit_interval(atmosphere, object_distance);
  result.rgb = attenuation_track(light_direction, origin, direction, atmosphere, result).rgb;
  return result;
}
