#version 410 core

float M_PI = 3.14159265358;

uniform sampler2D day;
uniform sampler2D night;
uniform sampler2D normals;
uniform sampler2D water;
uniform float specular;
uniform vec3 water_color;
uniform vec3 light_direction;
uniform vec3 origin;
uniform float albedo;
uniform float amplification;
uniform float reflectivity;
uniform float dawn_start;
uniform float dawn_end;

in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;

out vec4 fragColor;

vec3 direct_light(vec3 point);
vec3 attenuation_point(vec3 point, vec3 incoming);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
float remap(float value, float original_min, float original_max, float new_min, float new_max);
vec4 cloud_overlay();

// Render planet surface as seen through the atmosphere.
void main()
{
  vec3 land_normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 water_normal = normalize(fs_in.point);
  vec3 day_color = texture(day, fs_in.colorcoord).rgb;
  vec3 night_color = max(texture(night, fs_in.colorcoord).rgb - 0.3, 0.0) / 0.7;
  float wet = texture(water, fs_in.colorcoord).r;
  vec3 normal = mix(land_normal, water_normal, wet);
  float cos_normal = dot(light_direction, water_normal);
  vec3 light = direct_light(fs_in.point);
  vec3 ambient_light = surface_radiance_function(fs_in.point, light_direction);
  vec3 color = day_color * (1 - wet) + water_color * wet;
  float cos_incidence = dot(light_direction, normal);
  float highlight;
  if (cos_incidence > 0) {
    vec3 direction = normalize(fs_in.point - origin);
    highlight = pow(max(dot(reflect(light_direction, normal), direction), 0), specular);
  } else {
    cos_incidence = 0.0;
    highlight = 0.0;
  };
  vec3 diffuse = (albedo / M_PI) * color * (cos_incidence * light + ambient_light);
  vec3 specular = wet * reflectivity * highlight * light;
  vec3 night_lights = clamp(remap(cos_normal, dawn_start, dawn_end, 1.0, 0.0), 0.0, 1.0) * night_color;
  vec3 incoming = attenuation_point(fs_in.point, (diffuse + specular) * amplification + night_lights);
  vec4 cloud_scatter = cloud_overlay();
  fragColor = vec4(incoming, 1.0) * (1 - cloud_scatter.a) + cloud_scatter;
}
