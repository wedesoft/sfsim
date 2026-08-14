#version 450 core

<% (if full %>
uniform sampler2DArray day_night;
uniform sampler2D normals;
uniform sampler2D water;
uniform vec3 light_direction;
uniform float water_threshold;
uniform vec3 water_color;
uniform float reflectivity;
uniform float land_noise_scale;
uniform float land_noise_strength;
uniform float dawn_start;
uniform float dawn_end;
uniform mat4 world_to_camera;
<% ) %>

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec4 camera_point;
} fs_in;

layout (location = 0) out vec4 camera_point;
<% (if (not full) %>
layout (location = 1) out float dist;
<% ) %>
<% (if full %>
layout (location = 1) out vec4 camera_normal;
layout (location = 2) out vec4 diffuse_material;
layout (location = 3) out float metallic_material;
layout (location = 4) out float roughness_material;
layout (location = 5) out vec4 emissive_material;
<% ) %>

<% (if full %>
float land_noise(vec3 point);
float remap(float value, float original_min, float original_max, float new_min, float new_max);
<% ) %>

void main()
{
  camera_point = fs_in.camera_point;
<% (if (not full) %>
  dist = length(camera_point.xyz);
<% ) %>
<% (if full %>
  float wet = texture(water, fs_in.colorcoord).r >= water_threshold ? 1.0 : 0.0;
  vec3 world_point = fs_in.point;
  vec3 water_normal = normalize(world_point);
  vec3 land_normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 normal = mix(land_normal, water_normal, wet);
  camera_normal = world_to_camera * vec4(normal, 0);
  float land_modulation = 1.0 - land_noise_strength * land_noise(world_point / land_noise_scale);
  vec3 day_color = texture(day_night, vec3(fs_in.colorcoord, 0.25)).rgb * land_modulation;
  vec3 color = mix(day_color, water_color, wet);
  diffuse_material = vec4(color, 1.0);
  metallic_material = wet * reflectivity;
  vec3 night_color = max(texture(day_night, vec3(fs_in.colorcoord, 0.75)).rgb - 0.3, 0.0) / 0.7;
  vec3 emissive = clamp(remap(dot(light_direction, water_normal), dawn_start, dawn_end, 1.0, 0.0), 0.0, 1.0) * night_color;
  emissive_material = vec4(emissive, 0.0);
<% ) %>
}
