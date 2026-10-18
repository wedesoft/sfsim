#version 450 core

uniform vec3 origin;
uniform mat4 camera_to_world;
uniform mat4 camera_to_shockwave;
uniform vec3 light_direction;
uniform float radius;
uniform float max_height;
uniform float object_distance;
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
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec2 limit_interval(vec2 interval, float limit);
vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, vec2 segment, vec4 incoming);

<% (when outer %>
vec4 shockwave_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
{
  vec4 result = sample_shockwave_outer(object_origin, object_direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere = limit_interval(atmosphere, object_distance);
  result.rgb = attenuation_track(light_direction, origin, direction, atmosphere, result).rgb;
  return result;
}
<% ) %>

<% (when (not outer) %>
vec4 shockwave_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist)
{
  vec4 result = sample_shockwave_point(object_origin, object_direction, dist);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere = limit_interval(atmosphere, object_distance);
  result.rgb = attenuation_track(light_direction, origin, direction, atmosphere, result).rgb;
  return result;
}
<% ) %>

void main()
{
  vec4 camera_direction = vec4(normalize(geometry_point().xyz), 0);
  vec3 direction = mat3(camera_to_world) * camera_direction.xyz;
  vec3 object_origin_shockwave = (camera_to_shockwave * vec4(0, 0, 0, 1)).xyz;
  vec3 object_direction_shockwave = mat3(camera_to_shockwave) * camera_direction.xyz;
<% (when outer %>
  fragColor = shockwave_outer(origin, direction, object_origin_shockwave, object_direction_shockwave);
<% ) %>
<% (when (not outer) %>
  float dist = geometry_distance();
  fragColor = shockwave_point(origin, direction, object_origin_shockwave, object_direction_shockwave, dist);
<% ) %>
}
