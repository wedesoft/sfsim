#version 410 core

<% (doseq [[method-name shadow-map] parameters] %>
uniform sampler2DShadow <%= shadow-map %>;
<% ) %>

<% (doseq [[method-name shadow-map] parameters] %>
float <%= method-name %>(sampler2DShadow shadow_map, vec4 object_shadow_pos);
<% ) %>
vec3 environmental_shading(vec3 point);

vec3 overall_shading(vec3 world_point<%= (apply str (mapv #(str ", vec4 object_shadow_pos_" (inc ^long %)) (range (count parameters)))) %>)
{
  float object_shadows = 1.0;
<% (doseq [[i [method-name shadow-map]] (mapv list (range) parameters)] %>
  float object_shadow_<%= (inc ^long i) %> = <%= method-name %>(<%= shadow-map %>, <%= (str "object_shadow_pos_" (inc ^long i)) %>);
  if (object_shadow_<%= (inc ^long i) %> == 0.0)
    return vec3(0.0, 0.0, 0.0);
  object_shadows *= object_shadow_<%= (inc ^long i) %>;
<% ) %>
  return object_shadows * environmental_shading(world_point);
}
