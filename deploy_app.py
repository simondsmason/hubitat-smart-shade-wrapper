#!/usr/bin/env python3
"""
Hubitat App Deployment Script
Automates updating app code in Hubitat's web editor
Supports auto-discovery of editor IDs from Hubitat list pages
"""

import sys
import os
import re
from pathlib import Path

try:
    from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError
except ImportError:
    print("ERROR: playwright not installed. Installing...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "playwright", "--quiet"])
    subprocess.check_call([sys.executable, "-m", "playwright", "install", "chromium", "--with-deps"])
    from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

# Default hub IP (can be overridden)
DEFAULT_HUB_IP = "192.168.2.108"  # HubitatC8Pro

def find_editor_id(page, hub_ip, name, component_type="app"):
    """
    Auto-discover editor ID by searching the list page
    
    Args:
        page: Playwright page object
        hub_ip: Hubitat hub IP address
        name: Name of the app/driver to search for
        component_type: "app" or "driver"
    
    Returns:
        Editor ID as string, or None if not found
    """
    try:
        list_url = f"http://{hub_ip}/{component_type}/list"
        print(f"Searching for '{name}' in {component_type} list: {list_url}")
        page.goto(list_url, wait_until="domcontentloaded", timeout=30000)
        page.wait_for_timeout(2000)
        
        # Check for login
        login_inputs = page.query_selector_all("input[type='password']")
        if login_inputs:
            print("⚠️  Login required - waiting 10 seconds for manual login...")
            page.wait_for_timeout(10000)
        
        content = page.content()
        
        # Look for links to editor pages matching the name
        # Pattern: /app/editor/123 or /driver/editor/123
        pattern = rf'/{component_type}/editor/(\d+)'
        matches = re.finditer(pattern, content)
        
        for match in matches:
            editor_id = match.group(1)
            # Check if name appears near this editor link
            start_pos = max(0, match.start() - 200)
            end_pos = min(len(content), match.end() + 200)
            context = content[start_pos:end_pos]
            
            if name.lower() in context.lower():
                print(f"✓ Found {component_type} editor ID: {editor_id}")
                return editor_id
        
        # Alternative: search all links/table rows
        elements = page.query_selector_all("a, tr, td")
        for elem in elements:
            text = elem.inner_text() if hasattr(elem, 'inner_text') else ''
            href = elem.get_attribute('href') or ''
            
            if name.lower() in text.lower() and f'/{component_type}/editor/' in href:
                match = re.search(r'/editor/(\d+)', href)
                if match:
                    editor_id = match.group(1)
                    print(f"✓ Found {component_type} editor ID: {editor_id}")
                    return editor_id
        
        print(f"⚠️  Could not find editor ID for '{name}'")
        return None
    except Exception as e:
        print(f"Error finding {component_type} editor ID: {e}")
        return None

def deploy_app(app_url, app_code_path, headless=False):
    """
    Deploy app code to Hubitat editor
    
    Args:
        app_url: URL to Hubitat app editor (e.g., http://192.168.2.108/app/editor/1212)
        app_code_path: Path to the .groovy file
        headless: Run browser in headless mode
    """
    app_code_path = Path(app_code_path)
    
    if not app_code_path.exists():
        print(f"ERROR: App code file not found: {app_code_path}")
        return False
    
    # Read the app code
    with open(app_code_path, 'r', encoding='utf-8') as f:
        app_code = f.read()
    
    print(f"Loaded app code from: {app_code_path}")
    print(f"Code length: {len(app_code)} characters")
    
    with sync_playwright() as p:
        print("Launching browser...")
        browser = p.chromium.launch(headless=headless)
        context = browser.new_context()
        page = context.new_page()
        
        try:
            print(f"Navigating to: {app_url}")
            page.goto(app_url, wait_until="domcontentloaded", timeout=30000)
            
            # Wait a bit for page to fully load
            page.wait_for_timeout(3000)
            
            # Check if we need to login (look for login form)
            login_inputs = page.query_selector_all("input[type='password'], input[name*='password'], input[name*='Password']")
            if login_inputs:
                print("⚠️  Login page detected. Please enter credentials manually if needed.")
                print("Waiting 10 seconds for manual login...")
                page.wait_for_timeout(10000)
            
            # Wait for the code editor to be visible
            print("Waiting for CodeMirror editor to load...")
            
            # Hubitat uses CodeMirror - wait for it to be ready
            try:
                # Wait for CodeMirror container
                page.wait_for_selector(".CodeMirror", timeout=15000)
                print("✓ CodeMirror editor found")
                
                # Wait a bit more for CodeMirror to fully initialize
                page.wait_for_timeout(2000)
                
                # Update code using CodeMirror API
                print("Updating code in CodeMirror editor...")
                
                # Use JavaScript to access CodeMirror instance and set value
                result = page.evaluate(f"""
                    (function() {{
                        // Find CodeMirror editor
                        var cmElement = document.querySelector('.CodeMirror');
                        if (!cmElement) {{
                            return {{success: false, error: 'CodeMirror element not found'}};
                        }}
                        
                        // Get CodeMirror instance - it might be stored in different ways
                        var cm = null;
                        if (cmElement.CodeMirror) {{
                            cm = cmElement.CodeMirror;
                        }} else if (window.CodeMirror && cmElement.cm) {{
                            cm = cmElement.cm;
                        }} else {{
                            // Try to get from CodeMirror's internal structure
                            var editors = document.querySelectorAll('.CodeMirror');
                            for (var i = 0; i < editors.length; i++) {{
                                if (editors[i].CodeMirror) {{
                                    cm = editors[i].CodeMirror;
                                    break;
                                }}
                            }}
                        }}
                        
                        if (!cm) {{
                            // Fallback: try to find the textarea and update it directly
                            var textarea = document.querySelector('textarea');
                            if (textarea) {{
                                textarea.value = arguments[0];
                                // Trigger input event to notify CodeMirror
                                var event = new Event('input', {{ bubbles: true }});
                                textarea.dispatchEvent(event);
                                // Also trigger change event
                                var changeEvent = new Event('change', {{ bubbles: true }});
                                textarea.dispatchEvent(changeEvent);
                                return {{success: true, method: 'textarea'}};
                            }}
                            return {{success: false, error: 'CodeMirror instance not found'}};
                        }}
                        
                        // Set the value using CodeMirror API
                        cm.setValue(arguments[0]);
                        
                        // Trigger change event to mark code as modified (enables Save button)
                        if (cm.trigger) {{
                            cm.trigger('change');
                        }}
                        
                        // Also trigger via the textarea to ensure Hubitat detects the change
                        var textarea = cm.getTextArea();
                        if (textarea) {{
                            // Trigger multiple events to ensure Hubitat detects modification
                            var events = ['input', 'change', 'keyup'];
                            events.forEach(function(eventType) {{
                                var event = new Event(eventType, {{ bubbles: true }});
                                textarea.dispatchEvent(event);
                            }});
                        }}
                        
                        return {{success: true, method: 'codemirror'}};
                    }})
                """, app_code)
                
                if result.get('success'):
                    print(f"✓ Code updated successfully using {result.get('method', 'unknown')} method")
                else:
                    print(f"⚠️  Warning: {result.get('error', 'Unknown error')}")
                    # Try fallback method
                    textarea = page.query_selector("textarea")
                    if textarea:
                        print("Trying fallback: direct textarea update...")
                        textarea.fill(app_code)
                        print("✓ Code updated via textarea fallback")
                    else:
                        print("❌ Could not update code - no editor found")
                        return False
                        
            except PlaywrightTimeoutError:
                print("⚠️  CodeMirror not found, trying alternative methods...")
                # Try direct textarea approach
                textarea = page.query_selector("textarea")
                if textarea:
                    print("Found textarea, updating directly...")
                    textarea.fill(app_code)
                    print("✓ Code updated via textarea")
                else:
                    print("❌ No editor found")
                    return False
                
            # Look for Save button and click it
            print("\nLooking for Save button...")
            save_button = None
            
            # Wait a moment for UI to update after code change
            page.wait_for_timeout(1000)
            
            # Try different possible selectors for Save button
            save_selectors = [
                "button:has-text('Save')",
                "input[value='Save']",
                "button[type='submit']:has-text('Save')",
                ".btn-primary:has-text('Save')",
                "a:has-text('Save')",
                "button.btn-primary",
                "input.btn-primary[type='submit']",
                "[onclick*='save']",
                "[onclick*='Save']"
            ]
            
            for selector in save_selectors:
                try:
                    elements = page.query_selector_all(selector)
                    for elem in elements:
                        text = elem.inner_text() if hasattr(elem, 'inner_text') else elem.get_attribute('value') or ''
                        if 'save' in text.lower() or elem.get_attribute('onclick') and 'save' in elem.get_attribute('onclick').lower():
                            save_button = elem
                            print(f"✓ Found Save button with selector: {selector}")
                            break
                    if save_button:
                        break
                except Exception as e:
                    continue
            
            if save_button:
                print("Waiting for Save button to be enabled...")
                # Wait for button to be enabled (Hubitat may need a moment to register code changes)
                try:
                    page.wait_for_timeout(2000)  # Give Hubitat a moment to register changes
                    # Try waiting for enabled state with a longer timeout
                    save_button.wait_for_element_state("enabled", timeout=10000)
                    print("Save button is enabled")
                except Exception as e:
                    # If still not enabled, try clicking via JavaScript (bypasses enabled check)
                    print(f"Save button not enabled after wait, attempting JavaScript click...")
                    try:
                        save_button.evaluate("el => el.click()")
                        print("Clicked Save button via JavaScript")
                    except Exception as js_error:
                        print(f"JavaScript click also failed: {js_error}")
                        raise e
                else:
                    # Button is enabled, use normal click
                    print("Clicking Save button...")
                    save_button.scroll_into_view_if_needed()
                    save_button.click()
                
                # Wait for save to process and page to update
                print("Waiting for save to complete...")
                page.wait_for_timeout(3000)
                
                # Check for compilation errors
                print("Checking for compilation errors...")
                page.wait_for_timeout(1500)
                
                # Check for yellow warning banners (Hubitat's primary error display)
                warning_selectors = [
                    ".alert-warning",
                    ".alert-danger", 
                    ".warning",
                    "[class*='warning']",
                    "[class*='alert']",
                    "[class*='error']",
                ]
                
                errors_found = []
                for selector in warning_selectors:
                    try:
                        elements = page.query_selector_all(selector)
                        for elem in elements:
                            text = elem.inner_text()
                            if text and text.strip():
                                text_lower = text.lower()
                                if any(pattern in text_lower for pattern in [
                                    'expecting', 'found', '@ line', 'line ', 'column',
                                    'syntax error', 'compilation error', 'parse error',
                                    'unexpected', 'token', 'cannot', 'failed',
                                    'error', 'exception', 'invalid'
                                ]):
                                    clean_text = ' '.join(text.split())
                                    if clean_text not in errors_found:
                                        errors_found.append(clean_text)
                    except Exception as e:
                        continue
                
                # Check page text for error patterns
                try:
                    all_text = page.evaluate("() => document.body.innerText")
                    if all_text:
                        lines = all_text.split('\n')
                        for line in lines:
                            line_lower = line.lower().strip()
                            if '@ line' in line_lower or 'line ' in line_lower:
                                if any(keyword in line_lower for keyword in [
                                    'expecting', 'found', 'unexpected', 'token',
                                    'syntax', 'error', 'exception', 'cannot',
                                    'invalid', 'failed', 'parse'
                                ]):
                                    clean_line = ' '.join(line.split())
                                    if clean_line and clean_line not in errors_found:
                                        errors_found.append(clean_line)
                except Exception as e:
                    pass
                
                unique_errors = list(set(errors_found))
                
                if unique_errors:
                    print("\n❌ COMPILATION ERRORS FOUND:")
                    for error in unique_errors:
                        print(f"  - {error}")
                    return False
                else:
                    print("✅ No compilation errors detected. Code saved successfully!")
                    return True
            else:
                print("⚠️  Save button not found. Code updated but not saved.")
                return False
                
        except Exception as e:
            print(f"ERROR: {e}")
            import traceback
            traceback.print_exc()
            return False
        finally:
            browser.close()
            # Return focus to Cursor so user can see completion status
            import subprocess
            subprocess.run(['osascript', '-e', 'tell application "Cursor" to activate'])

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 deploy_app.py <code_path> [editor_url] [--headless] [--auto] [--hub-ip <ip>]")
        print("\nOptions:")
        print("  code_path       Path to the .groovy file (app or driver) (required)")
        print("  editor_url      Full editor URL (optional if using --auto)")
        print("  --auto          Auto-discover editor ID from name in code")
        print("  --hub-ip <ip>   Hubitat hub IP (default: 192.168.2.108)")
        print("  --headless      Run browser in headless mode")
        print("\nExamples:")
        print("  # Auto-discover:")
        print("  python3 deploy_app.py \"Hubitat-Smart-Shade-Wrapper - Child App.groovy\" --auto")
        sys.exit(1)
    
    # Parse arguments
    app_code_path = None
    app_url = None
    headless = False
    auto_discover = False
    hub_ip = DEFAULT_HUB_IP
    
    i = 1
    while i < len(sys.argv):
        arg = sys.argv[i]
        if arg == "--headless":
            headless = True
        elif arg == "--auto":
            auto_discover = True
        elif arg == "--hub-ip" and i + 1 < len(sys.argv):
            hub_ip = sys.argv[i + 1]
            i += 1
        elif arg.startswith("http://") or arg.startswith("https://"):
            app_url = arg
        elif not app_code_path:
            app_code_path = arg
        i += 1
    
    if not app_code_path:
        print("ERROR: app_code_path is required")
        sys.exit(1)
    
    # Auto-discover editor ID if requested
    if auto_discover and not app_url:
        code_path = Path(app_code_path)
        if not code_path.exists():
            print(f"ERROR: Code file not found: {app_code_path}")
            sys.exit(1)
        
        with open(code_path, 'r', encoding='utf-8') as f:
            code = f.read()
        
        # Determine component type (app or driver)
        is_app = 'definition(' in code or 'name: "' in code
        component_type = "app" if is_app else "driver"
        
        # Try to extract name from definition
        name_match = None
        if is_app:
            name_match = re.search(r'name:\s*["\']([^"\']+)["\']', code)
        else:
            name_match = re.search(r'name\s*=\s*["\']([^"\']+)["\']', code)
        
        if name_match:
            component_name = name_match.group(1)
        else:
            # Fallback: use filename
            component_name = code_path.stem.replace("-", " ").replace("_", " ")
        
        print(f"Auto-discovering {component_type} editor ID for: {component_name}")
        print(f"Hub IP: {hub_ip}")
        
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=False)  # Always visible for discovery
            context = browser.new_context()
            page = context.new_page()
            
            try:
                editor_id = find_editor_id(page, hub_ip, component_name, component_type)
                if editor_id:
                    app_url = f"http://{hub_ip}/{component_type}/editor/{editor_id}"
                    print(f"Using editor URL: {app_url}")
                else:
                    print(f"ERROR: Could not auto-discover {component_type} editor ID")
                    print("Please provide the editor URL manually or check the name")
                    browser.close()
                    import subprocess
                    subprocess.run(['osascript', '-e', 'tell application "Cursor" to activate'])
                    sys.exit(1)
            except Exception as e:
                print(f"ERROR during auto-discovery: {e}")
                browser.close()
                import subprocess
                subprocess.run(['osascript', '-e', 'tell application "Cursor" to activate'])
                sys.exit(1)
            finally:
                browser.close()
                import subprocess
                subprocess.run(['osascript', '-e', 'tell application "Cursor" to activate'])
    
    if not app_url:
        print("ERROR: app_url is required (provide URL or use --auto)")
        sys.exit(1)
    
    success = deploy_app(app_url, app_code_path, headless=headless)
    sys.exit(0 if success else 1)

