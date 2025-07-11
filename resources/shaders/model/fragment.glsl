#version 410 core

uniform vec3 light_direction;
<% (if textured %>
uniform sampler2D colors;
<% %>
uniform vec3 diffuse_color;
<% ) %>
<% (if bump %>
uniform sampler2D normals;
<% ) %>

in VS_OUT
{
  vec3 world_point;
  vec3 normal;
<% (if bump %>
  mat3 surface;
<% ) %>
<% (if (or textured bump) %>
  vec2 texcoord;
<% ) %>
<% (doseq [i (range num-scene-shadows)] %>
  vec4 object_shadow_pos_<%= (inc ^long i) %>;
<% ) %>
} fs_in;

out vec4 fragColor;

vec3 overall_shading(vec3 world_point<%= (apply str (map #(str ", vec4 object_shadow_pos_" (inc ^long %)) (range num-scene-shadows))) %>);
vec3 phong(vec3 ambient, vec3 light, vec3 point, vec3 normal, vec3 color, float reflectivity);
vec3 attenuation_point(vec3 point, vec3 incoming);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
vec4 cloud_point(vec3 point);

void main()
{
  vec3 light = overall_shading(fs_in.world_point<%= (apply str (map #(str ", fs_in.object_shadow_pos_" (inc ^long %)) (range num-scene-shadows))) %>);
  vec3 ambient_light = surface_radiance_function(fs_in.world_point, light_direction);
<% (if bump %>
  float cos_incidence_coarse = dot(light_direction, fs_in.normal);
  vec3 normal = cos_incidence_coarse > 0.0 ? fs_in.surface * (2.0 * texture(normals, fs_in.texcoord).xyz - 1.0) : fs_in.normal;
<% %>
  vec3 normal = fs_in.normal;
<% ) %>
<% (if textured %>
  vec3 diffuse_color = texture(colors, fs_in.texcoord).rgb;
<% ) %>
  vec3 incoming = phong(ambient_light, light, fs_in.world_point, normal, diffuse_color, 0.0);
  incoming = attenuation_point(fs_in.world_point, incoming);
  vec4 cloud_scatter = cloud_point(fs_in.world_point);
  fragColor = vec4(incoming, 1.0) * (1 - cloud_scatter.a) + cloud_scatter;
}
