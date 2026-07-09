import re
import os

files_to_process = {
    "HomeScreen.kt": "home",
    "SettingsScreen.kt": "settings",
    "TrainScreen.kt": "train",
    "NutritionScreen.kt": "nutrition",
    "ProgressScreen.kt": "progress",
    "ProgressDetailScreens.kt": "progress_detail",
    "PlanBuilderScreen.kt": "plan_builder",
    "Overlays.kt": "overlay",
    "PlateCalculator.kt": "plate_calc",
    "ApexFitApp.kt": "onboarding"
}

dir_path = "app/src/main/java/com/example/ui/screens"
strings_xml_path = "app/src/main/res/values/strings.xml"

extracted_strings = {}

def sanitize_key(s):
    # Remove variables like $units, ${...}
    s = re.sub(r'\$\{.*?\}', '', s)
    s = re.sub(r'\$[a-zA-Z0-9_]+', '', s)
    s = re.sub(r'[^a-zA-Z0-9]', '_', s).lower()
    s = re.sub(r'_+', '_', s).strip('_')
    return s[:30]

def replace_string(match, prefix, content):
    full_match = match.group(0)
    str_content = match.group(1)
    
    # Check if it has string interpolation
    if '$' in str_content:
        # Simplistic handling for 1 or 2 variables: e.g. "Weight ($units)" -> "Weight (%1$s)"
        # We can extract the vars
        vars = re.findall(r'\$\{([^}]+)\}|\$([a-zA-Z0-9_]+)', str_content)
        extracted_vars = [v[0] or v[1] for v in vars]
        
        fmt_str = str_content
        for i, v in enumerate(extracted_vars):
            var_syntax = "${" + v + "}" if "${" + v + "}" in fmt_str else "$" + v
            fmt_str = fmt_str.replace(var_syntax, f"%{i+1}$s")
            
        key_suffix = sanitize_key(str_content)
        if not key_suffix:
            key_suffix = "fmt_str"
        key = f"{prefix}_{key_suffix}"
        
        # Avoid duplicate keys with different content
        counter = 1
        orig_key = key
        while key in extracted_strings and extracted_strings[key] != fmt_str:
            key = f"{orig_key}_{counter}"
            counter += 1
            
        extracted_strings[key] = fmt_str
        
        args = ", ".join(extracted_vars)
        if "Text" in full_match:
            return f'Text(stringResource(R.string.{key}, {args})'
        elif "Toast" in full_match:
            return f'Toast.makeText(context, context.getString(R.string.{key}, {args})'
    else:
        key_suffix = sanitize_key(str_content)
        if not key_suffix:
            key_suffix = "str"
        key = f"{prefix}_{key_suffix}"
        
        counter = 1
        orig_key = key
        while key in extracted_strings and extracted_strings[key] != str_content:
            key = f"{orig_key}_{counter}"
            counter += 1
            
        extracted_strings[key] = str_content
        
        if "Text" in full_match:
            return f'Text(stringResource(R.string.{key})'
        elif "Toast" in full_match:
            return f'Toast.makeText(context, context.getString(R.string.{key})'

    return full_match

for filename, prefix in files_to_process.items():
    filepath = os.path.join(dir_path, filename)
    if not os.path.exists(filepath):
        continue
        
    with open(filepath, 'r') as f:
        content = f.read()
        
    # Replace Text("...")
    content = re.sub(r'Text\(\s*"([^"\\]*)"', lambda m: replace_string(m, prefix, content), content)
    
    # Replace Toast.makeText(context, "..."
    content = re.sub(r'Toast\.makeText\(context,\s*"([^"\\]*)"', lambda m: replace_string(m, prefix, content), content)
    
    # Ensure import is present
    if 'androidx.compose.ui.res.stringResource' not in content:
        content = content.replace('import androidx.compose.ui.Modifier', 'import androidx.compose.ui.res.stringResource\nimport androidx.compose.ui.Modifier')
        
    if 'import com.example.R' not in content:
        content = content.replace('import androidx.compose.ui.Modifier', 'import com.example.R\nimport androidx.compose.ui.Modifier')
        
    with open(filepath, 'w') as f:
        f.write(content)

# Update strings.xml
with open(strings_xml_path, 'r') as f:
    strings_content = f.read()
    
# Remove </resources>
strings_content = strings_content.replace('</resources>', '')

for key, val in extracted_strings.items():
    # Escape single quotes
    val = val.replace("'", "\\'")
    strings_content += f'    <string name="{key}">{val}</string>\n'
    
strings_content += '</resources>'

with open(strings_xml_path, 'w') as f:
    f.write(strings_content)

print(f"Extracted {len(extracted_strings)} strings.")
