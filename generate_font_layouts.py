import os

base_dir = "app/src/main/res/layout"
base_layouts = [
    "widget_layout.xml",
    "widget_layout_transparent_dark.xml",
    "widget_layout_transparent_light.xml"
]

fonts = {
    "serif": "serif",
    "mono": "monospace", 
    "cursive": "cursive",
    "condensed": "sans-serif-condensed",
    "condensed_light": "sans-serif-condensed-light",
    "light": "sans-serif-light",
    "medium": "sans-serif-medium", 
    "black": "sans-serif-black",
    "thin": "sans-serif-thin",
    "smallcaps": "sans-serif-smallcaps"
}

def generate_layouts():
    for base in base_layouts:
        src_path = os.path.join(base_dir, base)
        if not os.path.exists(src_path):
            print(f"Skipping {base}, not found.")
            continue
            
        with open(src_path, "r") as f:
            content = f.read()
            
        for font_suffix, font_family in fonts.items():
            new_filename = base.replace(".xml", f"_{font_suffix}.xml")
            dst_path = os.path.join(base_dir, new_filename)
            
            # Simple injection: replace android:layout_height="wrap_content" 
            # with android:layout_height="wrap_content" \n android:fontFamily="..."
            # This covers TextClock and TextViews in my layouts.
            
            new_content = content.replace(
                'android:layout_height="wrap_content"',
                f'android:layout_height="wrap_content"\n        android:fontFamily="{font_family}"'
            )
            
            # Also target EventText styles which might not have layout_height inline or match above
            new_content = new_content.replace(
                'style="@style/EventText"',
                f'style="@style/EventText" android:fontFamily="{font_family}"'
            )
            new_content = new_content.replace(
                'style="@style/EventText.TransparentDark"',
                f'style="@style/EventText.TransparentDark" android:fontFamily="{font_family}"'
            )
            new_content = new_content.replace(
                'style="@style/EventText.TransparentLight"',
                f'style="@style/EventText.TransparentLight" android:fontFamily="{font_family}"'
            )
            
            with open(dst_path, "w") as f:
                f.write(new_content)
            
            print(f"Generated {new_filename}")

if __name__ == "__main__":
    generate_layouts()
