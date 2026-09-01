// Temporary build-time probe: prints the public API surface of the MediaDevices
// package so the app can be written against the exact signatures. Not shipped.
using System.Reflection;
using System.Text;

var asm = Assembly.Load("MediaDevices");
Console.WriteLine($"### ASSEMBLY {asm.GetName().Name} {asm.GetName().Version}");

static string T(Type t)
{
    if (!t.IsGenericType) return t.Name;
    var args = string.Join(", ", t.GetGenericArguments().Select(T));
    return t.Name.Split('`')[0] + "<" + args + ">";
}

foreach (var type in asm.GetExportedTypes().OrderBy(t => t.FullName))
{
    var kind = type.IsEnum ? "enum" : type.IsInterface ? "interface" : type.IsValueType ? "struct" : "class";
    Console.WriteLine($"\n=== {kind} {type.FullName}{(type.BaseType is { } b && b != typeof(object) ? " : " + T(b) : "")}");

    if (type.IsEnum)
    {
        Console.WriteLine("    " + string.Join(", ", Enum.GetNames(type)));
        continue;
    }

    const BindingFlags F = BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static | BindingFlags.DeclaredOnly;

    foreach (var p in type.GetProperties(F).OrderBy(p => p.Name))
        Console.WriteLine($"    prop {T(p.PropertyType)} {p.Name} {{ {(p.CanRead ? "get; " : "")}{(p.CanWrite ? "set; " : "")}}}");

    foreach (var e in type.GetEvents(F).OrderBy(e => e.Name))
        Console.WriteLine($"    event {T(e.EventHandlerType!)} {e.Name}");

    foreach (var m in type.GetMethods(F).Where(m => !m.IsSpecialName).OrderBy(m => m.Name))
    {
        var ps = string.Join(", ", m.GetParameters().Select(p =>
            $"{T(p.ParameterType)} {p.Name}{(p.HasDefaultValue ? " = " + (p.DefaultValue ?? "null") : "")}"));
        Console.WriteLine($"    {(m.IsStatic ? "static " : "")}{T(m.ReturnType)} {m.Name}({ps})");
    }

    foreach (var c in type.GetConstructors(BindingFlags.Public | BindingFlags.Instance | BindingFlags.DeclaredOnly))
        Console.WriteLine($"    ctor ({string.Join(", ", c.GetParameters().Select(p => $"{T(p.ParameterType)} {p.Name}"))})");
}
