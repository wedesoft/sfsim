#version 450 core

uniform float radius;
uniform float max_height;
uniform float object_distance;
uniform vec3 light_direction;
uniform float amplification;
uniform float opacity_cutoff;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 cloud_outer(vec3 origin, vec3 direction, float skip);
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment);
vec4 plume_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction);
vec4 plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);

vec4 blend(vec4 front, vec4 back)
{
  return vec4(front.rgb + back.rgb * (1.0 - front.a), 1.0 - (1.0 - front.a) * (1.0 - back.a));
}

// Cloud and plume mixed overlay.
// origin: world coordinate of camera position
// direction: world coordinate of viewing direction for current pixel rendered by fragment shader.
// point: world coordinates of point of planet surface
// object_origin: camera position coordinates in object (plume) coordinate system.
// object_direction: viewing direction coordinates in object (plume) coordinate system.
// object_point: model surface point in object (plume) coordinate system.

<% (if (and (not planet-point) (not model-point)) %>
// Shader to render overlay with clouds, then plume, and then clouds again with outer space in the background.
vec4 cloud_plume_cloud(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
<% ) %>
<% (if (and planet-point (not model-point)) %>
// Shader to render overlay with clouds, then plume, and then clouds again with planet in the background.
vec4 cloud_plume_cloud_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist)
<% ) %>
<% (if model-point %>
// Shader to render overlay with clouds, then plume with model in the background.
vec4 cloud_plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist)
<% ) %>
{
  vec4 result;
<% (if planet-point %>
  float min_distance = min(object_distance, dist);
  result = cloud_point(origin, direction, vec2(0, min_distance));
<% %>
  result = cloud_point(origin, direction, vec2(0, object_distance));
<% ) %>
<% (if (or model-point planet-point) %>
  vec4 plume = plume_point(origin, direction, object_origin, object_direction, dist);
<% %>
  vec4 plume = plume_outer(origin, direction, object_origin, object_direction);
<% ) %>
  result = blend(result, plume);
<% (when (not model-point) %>
<% (if planet-point %>
  if (object_distance <= min_distance && result.a <= 1.0 - opacity_cutoff) {
    vec4 cloud_scatter = cloud_point(origin, direction, vec2(object_distance, dist - object_distance));
<% %>
    vec4 cloud_scatter = cloud_outer(origin, direction, object_distance);
<% ) %>
    result = blend(result, cloud_scatter);
<% (if planet-point %>
  };
<% ) %>
<% ) %>
  return result;
}
