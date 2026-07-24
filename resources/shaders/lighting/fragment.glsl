#version 450 core

uniform sampler2D camera_point;
uniform sampler2D camera_normal;
uniform sampler2D diffuse_material;
uniform sampler2D specular_material;
uniform sampler2D emissive_material;
uniform mat4 camera_to_world;
uniform vec3 origin;
<% (doseq [i (range num-scene-shadows)] %>
uniform mat4 camera_to_shadow_map_<%= (inc ^long i) %>;
<% ) %>
uniform float radius;
uniform float max_height;
uniform float z_far;
uniform vec3 light_direction;
uniform int width;
uniform int height;

out vec4 fragColor;

vec3 overall_shading(vec3 world_point<%= (apply str (map #(str ", vec4 object_shadow_pos_" (inc ^long %)) (range num-scene-shadows))) %>);
vec3 phong(vec3 ambient, vec3 light, vec3 point, vec3 normal, vec3 color, float reflectivity);
vec4 attenuation_point(vec3 point, vec4 incoming);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 attenuation_outer(vec3 light_direction, vec3 origin, vec3 direction, float a, vec3 incoming);
vec4 cloud_overlay(float depth);

void main()
{
  vec2 uv = vec2(gl_FragCoord.x / width, gl_FragCoord.y / height);
  vec4 point = texture(camera_point, uv);
  if (point.w > 0.0) {
    vec4 normal = texture(camera_normal, uv);
    vec3 diffuse_color = texture(diffuse_material, uv).rgb;
    vec3 world_point = (camera_to_world * point).xyz;
    vec3 ambient_light = surface_radiance_function(world_point, light_direction);
<% (doseq [i (range num-scene-shadows)] %>
    vec4 object_shadow_pos_<%= (inc ^long i) %> = camera_to_shadow_map_<%= (inc ^long i) %> * point;
<% ) %>
    vec3 light = overall_shading(world_point <%= (apply str (map #(str ", object_shadow_pos_" (inc ^long %)) (range num-scene-shadows))) %>);
    float specular = texture(specular_material, uv).r;
    vec3 phong = phong(ambient_light, light, world_point, normal.xyz, diffuse_color, specular);
    vec3 emissive = texture(emissive_material, uv).rgb;
    vec3 incoming = attenuation_point(world_point, vec4(phong + emissive, 1.0)).rgb;
    vec4 cloud_scatter = cloud_overlay(length(point.xyz));
    fragColor = vec4(incoming.rgb * (1 - cloud_scatter.a) + cloud_scatter.rgb, 1.0);
  } else {
    vec3 direction = (camera_to_world * point).xyz;
    vec3 incoming = texture(emissive_material, uv).rgb;
    vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
    if (atmosphere_intersection.y > 0) {
      incoming = attenuation_outer(light_direction, origin, direction, atmosphere_intersection.x, incoming);
    };
    vec4 cloud_scatter = cloud_overlay(z_far);
    incoming = incoming * (1 - cloud_scatter.a) + cloud_scatter.rgb;
    fragColor = vec4(incoming, 1);
  }
}
