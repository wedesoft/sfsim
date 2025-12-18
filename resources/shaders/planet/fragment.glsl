#version 450 core

uniform sampler2DArray day_night;
uniform sampler2D normals;
uniform sampler2D water;
uniform float water_threshold;
uniform vec3 water_color;
uniform vec3 light_direction;
uniform float reflectivity;
uniform float land_noise_scale;
uniform float land_noise_strength;
uniform float dawn_start;
uniform float dawn_end;

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
<% (doseq [i (range num-scene-shadows)] %>
  vec4 object_shadow_pos_<%= (inc ^long i) %>;
<% ) %>
} fs_in;

out vec4 fragColor;

vec3 overall_shading(vec3 world_point<%= (apply str (map #(str ", vec4 object_shadow_pos_" (inc ^long %)) (range num-scene-shadows))) %>);
vec3 environmental_shading(vec3 point);
vec3 phong(vec3 ambient, vec3 light, vec3 point, vec3 normal, vec3 color, float reflectivity);
vec4 attenuation_point(vec3 point, vec4 incoming);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
float land_noise(vec3 point);
float remap(float value, float original_min, float original_max, float new_min, float new_max);
vec4 cloud_overlay(float dist);

// Render planet surface as seen through the atmosphere.
void main()
{
  float wet = texture(water, fs_in.colorcoord).r >= water_threshold ? 1.0 : 0.0;
  vec3 land_normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 water_normal = normalize(fs_in.point);
  vec3 normal = mix(land_normal, water_normal, wet);
  vec3 light = overall_shading(fs_in.point<%= (apply str (map #(str ", fs_in.object_shadow_pos_" (inc ^long %)) (range num-scene-shadows))) %>);
  vec3 ambient_light = surface_radiance_function(fs_in.point, light_direction);
  float land_modulation = 1.0 - land_noise_strength * land_noise(fs_in.point / land_noise_scale);
  vec3 day_color = texture(day_night, vec3(fs_in.colorcoord, 0.25)).rgb * land_modulation;
  vec3 color = mix(day_color, water_color, wet);
  vec3 night_color = max(texture(day_night, vec3(fs_in.colorcoord, 0.75)).rgb - 0.3, 0.0) / 0.7;
  vec3 emissive = clamp(remap(dot(light_direction, water_normal), dawn_start, dawn_end, 1.0, 0.0), 0.0, 1.0) * night_color;
  vec3 phong = phong(ambient_light, light, fs_in.point, normal, color, wet * reflectivity);
  vec4 incoming = attenuation_point(fs_in.point, vec4(phong + emissive, 1.0));
  vec4 cloud_scatter = cloud_overlay(length(fs_in.camera_point.xyz));
  fragColor = vec4(incoming.rgb * (1 - cloud_scatter.a) + cloud_scatter.rgb, 1.0);
}
