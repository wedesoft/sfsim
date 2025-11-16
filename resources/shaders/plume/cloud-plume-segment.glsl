#version 410 core

uniform float radius;
uniform float max_height;
uniform float object_distance;
uniform vec3 light_direction;
uniform float amplification;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 cloud_outer(vec3 origin, vec3 direction);
vec4 cloud_point(vec3 origin, vec3 direction, vec3 point);
vec4 plume_outer(vec3 object_origin, vec3 object_direction);
vec4 plume_point(vec3 object_origin, vec3 object_direction, vec3 object_point);
vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);

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
vec4 cloud_plume_cloud_point(vec3 origin, vec3 direction, vec3 point, vec3 object_origin, vec3 object_direction, vec3 object_point)
<% ) %>
<% (if model-point %>
// Shader to render overlay with clouds, then plume with model in the background.
vec4 cloud_plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, vec3 object_point)
<% ) %>
{
<% (if planet-point %>
  float min_distance = min(object_distance, distance(origin, point));
  vec4 result = cloud_point(origin, direction, origin + direction * min_distance);
<% %>
  vec4 result = cloud_point(origin, direction, origin + direction * object_distance);
<% ) %>
<% (if (or model-point planet-point) %>
  vec4 plume = plume_point(object_origin, object_direction, object_point);
<% %>
  vec4 plume = plume_outer(object_origin, object_direction);
<% ) %>
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere.y = min(atmosphere.y, object_distance - atmosphere.x);
  vec3 transmittance;
  vec3 in_scatter;
  if (atmosphere.y > 0) {
    vec3 start = origin + direction * atmosphere.x;
    vec3 end = origin + direction * (atmosphere.x + atmosphere.y);
    transmittance = transmittance_track(start, end);
    in_scatter = ray_scatter_track(light_direction, start, end) * amplification;
  } else {
    transmittance = vec3(1, 1, 1);
    in_scatter = vec3(0, 0, 0);
  };
  plume.rgb = plume.rgb * transmittance + plume.a * in_scatter;
  result = vec4(result.rgb + plume.rgb * (1.0 - result.a), 1.0 - (1.0 - plume.a) * (1.0 - result.a));
<% (if planet-point %>
  if (object_distance <= min_distance) {
<% ) %>
<% (if (and planet-point (not model-point)) %>
  vec4 cloud_scatter = cloud_point(origin + direction * object_distance, direction, point);
<% ) %>
<% (if (and (not planet-point) (not model-point)) %>
  vec4 cloud_scatter = cloud_outer(origin + direction * object_distance, direction);
<% ) %>
<% (if (not model-point) %>
  cloud_scatter.rgb = cloud_scatter.rgb * transmittance + cloud_scatter.a * in_scatter;
  result = vec4(result.rgb + cloud_scatter.rgb * (1.0 - result.a), 1.0 - (1.0 - cloud_scatter.a) * (1.0 - result.a));
<% ) %>
<% (if planet-point %>
  };
<% ) %>
  return result;
}
